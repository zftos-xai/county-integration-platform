package cn.zqkj.platform.masterdata.service.hospitaldirectory.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.his.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryEntry;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryType;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisBusinessException;
import cn.zqkj.platform.his.exception.PhisConfigurationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectorySyncResult;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectorySyncResultStatus;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryRelationRecord;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.masterdata.mapper.hospitaldirectory.HospitalDirectorySyncMapper;
import cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper;
import cn.zqkj.platform.masterdata.service.hospitaldirectory.HospitalDirectorySyncService;
import cn.zqkj.platform.masterdata.service.batch.MasterDataBatchService;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将100-003的四类目录按来源目录类型独立对账。
 *
 * <p>外部HIS调用永远在事务外执行。每个目录类型完整返回且通过自动校验后，立即在短事务中更新当前目录，
 * 并仅将该类型中HIS未返回的旧记录标为无效。任何失败类型都不会改动其旧数据，也不阻断其他类型。</p>
 */
@Service
public class HospitalDirectorySyncServiceImpl implements HospitalDirectorySyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HospitalDirectorySyncServiceImpl.class);
    private static final String SOURCE_SYSTEM_CODE = "PRIMARY_HIS";
    private final HospitalDirectorySyncMapper mapper;
    private final MasterDataBatchMapper batchMapper;
    private final MasterDataBatchService batchService;
    private final PhisService phisService;
    private final ManagementAuditService auditService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建医院综合目录同步服务并注入批次、HIS和持久化依赖。
     *
     * @param mapper 综合目录持久化边界
     * @param batchMapper 批次读取边界
     * @param batchService 批次权限内读取服务
     * @param phisService HIS强类型调用服务
     * @param auditService 管理审计服务
     * @param transactionTemplate 短事务模板
     */
    public HospitalDirectorySyncServiceImpl(HospitalDirectorySyncMapper mapper, MasterDataBatchMapper batchMapper,
                                            MasterDataBatchService batchService, PhisService phisService,
                                            ManagementAuditService auditService, TransactionTemplate transactionTemplate) {
        this.mapper = mapper;
        this.batchMapper = batchMapper;
        this.batchService = batchService;
        this.phisService = phisService;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * {@inheritDoc}
     *
     * <p>先以乐观锁占用批次，再按固定依赖顺序在事务外调用HIS；每个完整通过校验的目录类型
     * 独立短事务落库。失败或结果未知的类型保留原有效数据，避免把不完整返回误判为删除。</p>
     */
    @Override
    public MasterDataBatchSummaryVO fetchValidateAndReconcile(long batchId, byte[] expectedVersion, AccessActor actor) {
        MasterDataBatchSnapshot batch = requireHospitalDirectoryBatch(batchId, actor);
        if (expectedVersion == null || expectedVersion.length == 0) {
            throw new InvalidRequestException("必须提供最近读取的批次版本");
        }
        long organizationId = requireOrganizationId(batch.organizationCode());
        transactionTemplate.executeWithoutResult(ignored -> {
            if (mapper.beginFetch(batchId, expectedVersion, actor.loginName()) != 1) {
                throw new ResourceConflictException("批次状态已经变化，请重新读取后再操作");
            }
        });

        List<TypeSyncOutcome> outcomes = new ArrayList<>();
        Set<HospitalDirectoryType> synchronizedTypes = new HashSet<>();
        for (HospitalDirectoryType type : syncOrder()) {
            TypeSyncOutcome outcome = fetchValidateAndSynchronize(batchId, organizationId, batch, type, synchronizedTypes);
            outcomes.add(outcome);
            if (outcome.completed()) synchronizedTypes.add(type);
        }
        finishRun(batchId, organizationId, batch, outcomes, actor);
        return batchService.get(batchId, actor);
    }

    /**
     * 返回符合目录依赖关系的固定同步顺序。
     *
     * @return 先同步被其他目录引用的目标类型，保证同一次运行能更新可验证的关系。
     */
    private List<HospitalDirectoryType> syncOrder() {
        return List.of(HospitalDirectoryType.DEPARTMENT, HospitalDirectoryType.WARD,
                HospitalDirectoryType.BED, HospitalDirectoryType.DOCTOR);
    }

    /**
     * 将平台目录分类转换为当前HIS 100-003请求参数。
     *
     * @param type 平台目录分类
     * @return HIS请求所需目录分类
     */
    private cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryType hisType(
            HospitalDirectoryType type
    ) {
        return cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryType.valueOf(type.name());
    }

    /**
     * 取得、校验并对账一个目录类型。任何外部调用和本地写入错误都被限制在该类型，不影响其他类型。
     *
     * @param batchId 同步运行主键
     * @param organizationId 平台机构主键
     * @param batch 运行范围快照
     * @param type 当前目录类型
     * @param synchronizedTypes 本次已经完整成功的目录类型
     * @return 当前类型的可追溯处理结果
     */
    private TypeSyncOutcome fetchValidateAndSynchronize(long batchId, long organizationId, MasterDataBatchSnapshot batch,
                                                         HospitalDirectoryType type,
                                                         Set<HospitalDirectoryType> synchronizedTypes) {
        ValidationResult validation;
        try {
            PhisResponse<List<HospitalDirectoryEntry>> response = phisService.queryHospitalDirectory(organizationId,
                    batch.environment(), new HospitalDirectoryQuery(hisType(type), null, batch.sourceOrganizationId()));
            if (!response.success()) {
                return TypeSyncOutcome.failed(type, false, 0, safeFailure(response.errorMessage()));
            }
            validation = validate(type, response.data() == null ? List.of() : response.data(), synchronizedTypes);
        } catch (PhisCommunicationException | PhisProtocolException exception) {
            return TypeSyncOutcome.failed(type, true, 0, "未能确认" + typeLabel(type) + "查询结果，请先确认HIS交易记录后再发起新的同步");
        } catch (PhisConfigurationException exception) {
            return TypeSyncOutcome.failed(type, false, 0, safeFailure(exception.getMessage()));
        } catch (PhisBusinessException exception) {
            return TypeSyncOutcome.failed(type, false, 0, safeFailure(exception.getMessage()));
        }
        if (!validation.valid()) {
            return TypeSyncOutcome.failed(type, false, validation.returned(), validation.failureSummary(), validation);
        }
        try {
            DirectoryMutationCounts counts = transactionTemplate.execute(ignored -> synchronizeType(
                    batchId, organizationId, type, validation));
            return TypeSyncOutcome.completed(type, validation, counts);
        } catch (RuntimeException exception) {
            LOGGER.error("100-003目录对账失败｜批次{}｜类型{}｜{}", batchId, type.code(), technicalFailure(exception));
            return TypeSyncOutcome.failed(type, false, validation.returned(), "平台保存" + typeLabel(type) + "失败，未确认的旧数据未标记无效", validation);
        }
    }

    /**
     * 对单一目录类型的完整返回范围执行自动校验。
     *
     * @param type 目录类型
     * @param entries 当前类型完整返回记录
     * @param synchronizedTypes 已完整成功类型
     * @return 当前类型的自动校验结果
     */
    private ValidationResult validate(HospitalDirectoryType type, List<HospitalDirectoryEntry> entries,
                                      Set<HospitalDirectoryType> synchronizedTypes) {
        Map<String, HospitalDirectorySourceRecord> accepted = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        long returned = 0;
        long duplicates = 0;
        long invalid = 0;
        long conflicts = 0;
        Set<HospitalDirectoryRelationRecord> relations = new HashSet<>();
        Set<String> rejected = new HashSet<>();
        for (HospitalDirectoryEntry entry : entries) {
            returned++;
            HospitalDirectorySourceRecord record = toRecord(type, entry, failures);
            if (record == null) {
                invalid++;
                continue;
            }
            HospitalDirectoryRelationRecord relation = toRelation(type, record);
            record = toMasterRecord(record);
            HospitalDirectorySourceRecord previous = accepted.putIfAbsent(record.sourceCode(), record);
            if (previous == null && !rejected.contains(record.sourceCode())) {
                if (relation != null && relationTargetWasSynchronized(relation, synchronizedTypes)) relations.add(relation);
                continue;
            }
            if (previous != null && previous.equals(record)) {
                duplicates++;
                if (relation != null && relationTargetWasSynchronized(relation, synchronizedTypes)) relations.add(relation);
            } else if (previous != null) {
                conflicts++;
                accepted.remove(record.sourceCode());
                rejected.add(record.sourceCode());
                failures.add(typeLabel(type) + "编码" + record.sourceCode() + "返回了互相冲突的内容");
            }
        }
        String summary = failures.isEmpty() ? null : safeFailure(String.join("；", failures));
        return new ValidationResult(returned, duplicates, invalid, conflicts, List.copyOf(accepted.values()), List.copyOf(relations),
                failures.isEmpty(), summary);
    }

    /**
     * 移除展开行中的关系字段，生成正式目录主记录。
     *
     * @param record 展开行
     * @return 不包含多对多关联的稳定目录主记录
     */
    private HospitalDirectorySourceRecord toMasterRecord(HospitalDirectorySourceRecord record) {
        return switch (record.directoryType()) {
            case DOCTOR -> new HospitalDirectorySourceRecord(record.directoryType(), record.sourceCode(),
                    record.sourceName(), record.mnemonicCode(), record.categoryName(), null,
                    null, null, null, record.sourceOrganizationCode());
            case WARD -> new HospitalDirectorySourceRecord(record.directoryType(), record.sourceCode(),
                    record.sourceName(), record.mnemonicCode(), record.categoryName(), record.remark(),
                    null, null, null, record.sourceOrganizationCode());
            case BED -> new HospitalDirectorySourceRecord(record.directoryType(), record.sourceCode(),
                    record.sourceName(), record.mnemonicCode(), record.categoryName(), null,
                    null, null, null, record.sourceOrganizationCode());
            case DEPARTMENT -> record;
        };
    }

    /**
     * 从医生、病区或床位展开行生成结构化目录关系。
     *
     * @param type 目录类型
     * @param record 原始展开行
     * @return 可选的结构化关联
     */
    private HospitalDirectoryRelationRecord toRelation(HospitalDirectoryType type,
                                                        HospitalDirectorySourceRecord record) {
        return switch (type) {
            case DOCTOR -> record.wardName() == null ? null
                    : new HospitalDirectoryRelationRecord("DOCTOR_WARD", record.sourceCode(), record.wardName());
            case WARD -> record.departmentCode() == null ? null
                    : new HospitalDirectoryRelationRecord("WARD_DEPARTMENT", record.sourceCode(),
                    record.departmentCode());
            case BED -> record.remark() == null ? null
                    : new HospitalDirectoryRelationRecord("BED_WARD", record.sourceCode(), record.remark());
            case DEPARTMENT -> null;
        };
    }

    /**
     * 校验并转换一条HIS医院综合目录记录。
     *
     * @param type 目录类型
     * @param entry 原始HIS记录
     * @param failures 业务错误集合
     * @return 可持久化的非敏感记录；字段无效时为空
     */
    private HospitalDirectorySourceRecord toRecord(HospitalDirectoryType type, HospitalDirectoryEntry entry,
                                                  List<String> failures) {
        if (entry == null) {
            failures.add(typeLabel(type) + "返回空记录");
            return null;
        }
        String code = normalize(entry.directoryCode());
        String name = normalize(entry.directoryName());
        if (code == null || name == null || exceeds(code, 50) || exceeds(name, 50)
                || exceeds(entry.mnemonicCode(), 20) || exceeds(entry.categoryName(), 20)
                || exceeds(entry.remark(), 100) || exceeds(entry.departmentCode(), 50)
                || exceeds(entry.departmentName(), 50) || exceeds(entry.ward(), 50)
                || exceeds(entry.organizationCode(), 50)) {
            failures.add(typeLabel(type) + "存在缺失或超长的关键字段");
            return null;
        }
        return new HospitalDirectorySourceRecord(type, code, name, normalize(entry.mnemonicCode()),
                normalize(entry.categoryName()), normalize(entry.remark()), normalize(entry.departmentCode()),
                normalize(entry.departmentName()), normalize(entry.ward()), normalize(entry.organizationCode()));
    }

    /**
     * 判断一条关联的目标目录是否已经在本次运行完成对账。
     *
     * <p>主目录不因可选关联被丢弃；当被引用类型本次未完整成功时，仅不替换该关系，避免写入无法证明的关联。</p>
     *
     * @param relation 候选关系
     * @param synchronizedTypes 本次已完成对账的目录类型
     * @return 可安全替换关系时为true
     */
    private boolean relationTargetWasSynchronized(HospitalDirectoryRelationRecord relation,
                                                   Set<HospitalDirectoryType> synchronizedTypes) {
        return switch (relation.relationType()) {
            case "WARD_DEPARTMENT" -> synchronizedTypes.contains(HospitalDirectoryType.DEPARTMENT);
            case "DOCTOR_WARD", "BED_WARD" -> synchronizedTypes.contains(HospitalDirectoryType.WARD);
            default -> false;
        };
    }

    /**
     * 在一个短事务内写入一个已经完整成功的目录类型，并且仅使该类型缺失记录失效。
     *
     * @param batchId 同步运行主键
     * @param organizationId 平台机构主键
     * @param type 已完整取得的目录类型
     * @param result 已通过自动校验的结果
     * @return 当前类型对账产生的数量事实
     */
    private DirectoryMutationCounts synchronizeType(long batchId, long organizationId, HospitalDirectoryType type,
                                                    ValidationResult result) {
        long created = 0;
        long updated = 0;
        long unchanged = 0;
        for (HospitalDirectorySourceRecord record : result.records()) {
            int classification = mapper.classifyDirectory(organizationId, record);
            if (classification == 0) {
                mapper.insertDirectoryDirect(batchId, organizationId, record);
                created++;
            } else {
                mapper.updateDirectoryDirect(batchId, organizationId, record);
                if (classification == 1) updated++;
                else unchanged++;
            }
        }
        long sourceMissing = mapper.markMissingDirectoryInvalid(batchId, organizationId, type);
        mapper.deleteRelationsForType(batchId, organizationId, type);
        result.relations().forEach(relation -> mapper.insertRelationDirect(batchId, organizationId, relation));
        return new DirectoryMutationCounts(created, updated, unchanged, sourceMissing);
    }

    /**
     * 汇总四类目录结果，在同一短事务中结束同步运行并追加成功审计。
     *
     * @param batchId 同步运行主键
     * @param organizationId 平台机构主键
     * @param batch 批次范围快照
     * @param outcomes 每个目录类型的处理结果
     * @param actor 发起人
     */
    private void finishRun(long batchId, long organizationId, MasterDataBatchSnapshot batch,
                           List<TypeSyncOutcome> outcomes, AccessActor actor) {
        long returned = outcomes.stream().mapToLong(TypeSyncOutcome::returned).sum();
        long duplicates = outcomes.stream().mapToLong(outcome -> outcome.validation() == null ? 0 : outcome.validation().duplicates()).sum();
        long invalid = outcomes.stream().mapToLong(outcome -> outcome.validation() == null ? 0 : outcome.validation().invalid()).sum();
        long conflicts = outcomes.stream().mapToLong(outcome -> outcome.validation() == null ? 0 : outcome.validation().conflicts()).sum();
        long created = outcomes.stream().mapToLong(outcome -> outcome.counts() == null ? 0 : outcome.counts().created()).sum();
        long updated = outcomes.stream().mapToLong(outcome -> outcome.counts() == null ? 0 : outcome.counts().updated()).sum();
        long unchanged = outcomes.stream().mapToLong(outcome -> outcome.counts() == null ? 0 : outcome.counts().unchanged()).sum();
        long sourceMissing = outcomes.stream().mapToLong(outcome -> outcome.counts() == null ? 0 : outcome.counts().sourceMissing()).sum();
        boolean unknown = outcomes.stream().anyMatch(TypeSyncOutcome::unknown);
        boolean allCompleted = outcomes.stream().allMatch(TypeSyncOutcome::completed);
        String status = allCompleted ? "COMPLETED" : unknown ? "COMPLETED_WITH_UNKNOWN" : "COMPLETED_WITH_ERRORS";
        String failureCode = allCompleted ? null : unknown ? "HIS_PARTIAL_RESULT_UNKNOWN" : "HIS_PARTIAL_FAILURE";
        String summary = allCompleted ? null : summarizeFailures(outcomes);
        ManagementAuditCommand auditCommand = new ManagementAuditCommand(
                actor, null, organizationId, batch.organizationCode(), "MASTER_DATA_HOSPITAL_DIRECTORY_SYNCED",
                "MASTER_DATA_BATCH", batch.batchNo(), allCompleted ? "SUCCESS" : "PARTIAL",
                "100-003目录直接对账：" + created + "新增，" + updated + "更新，" + sourceMissing + "条标记无效", null
        );
        transactionTemplate.executeWithoutResult(ignored -> {
            long active = mapper.countActive(organizationId);
            outcomes.forEach(outcome -> mapper.saveDirectoryResult(batchId,
                    toDirectoryResult(organizationId, outcome)));
            if (mapper.finishCompleted(batchId, status, returned, duplicates, invalid, conflicts, created, updated,
                    unchanged, sourceMissing, active, failureCode, summary, actor.loginName()) != 1) {
                throw new ResourceConflictException("同步运行状态已经变化，请重新读取后再操作");
            }
            auditService.recordSuccess(auditCommand);
        });
    }

    /**
     * 将运行期单目录结果转换为可持久化的分项事实。
     *
     * <p>只有完整通过自动校验并完成当前数据更新的类型才记录有效数量；失败和结果未知类型
     * 不以旧数据数量伪装为本次处理结果。</p>
     *
     * @param organizationId 平台机构主键
     * @param outcome 单一目录类型运行结果
     * @return 可供结果页和后续核对读取的分项事实
     */
    private HospitalDirectorySyncResult toDirectoryResult(long organizationId, TypeSyncOutcome outcome) {
        ValidationResult validation = outcome.validation();
        DirectoryMutationCounts counts = outcome.counts();
        HospitalDirectorySyncResultStatus status = outcome.completed()
                ? HospitalDirectorySyncResultStatus.COMPLETED
                : outcome.unknown() ? HospitalDirectorySyncResultStatus.RESULT_UNKNOWN
                : HospitalDirectorySyncResultStatus.FAILED;
        return new HospitalDirectorySyncResult(
                outcome.type(),
                status,
                outcome.returned(),
                validation == null ? 0 : validation.duplicates(),
                validation == null ? 0 : validation.invalid(),
                validation == null ? 0 : validation.conflicts(),
                counts == null ? 0 : counts.created(),
                counts == null ? 0 : counts.updated(),
                counts == null ? 0 : counts.unchanged(),
                counts == null ? 0 : counts.sourceMissing(),
                outcome.completed() ? mapper.countActiveByType(organizationId, outcome.type()) : null,
                outcome.completed() ? null : outcome.failureSummary()
        );
    }

    /**
     * 合并自动校验失败原因并限制数据库存储长度。
     *
     * @param outcomes 目录类型处理结果
     * @return 可面向业务人员展示的受控摘要
     */
    private String summarizeFailures(List<TypeSyncOutcome> outcomes) {
        String summary = outcomes.stream().filter(outcome -> !outcome.completed())
                .map(outcome -> typeLabel(outcome.type()) + "：" + outcome.failureSummary())
                .reduce((left, right) -> left + "；" + right).orElse("部分目录未完成同步");
        return safeFailure(summary);
    }

    /**
     * 读取并校验可执行的医院综合目录同步批次。
     *
     * @param batchId 批次主键
     * @param actor 发起人
     * @return 有权限的医院综合目录批次
     */
    private MasterDataBatchSnapshot requireHospitalDirectoryBatch(long batchId, AccessActor actor) {
        MasterDataBatchSnapshot batch = batchMapper.findById(batchId);
        if (batch == null) throw new InvalidRequestException("同步批次不存在");
        if (batch.organizationCode() == null || !actor.canAccess(batch.organizationCode())) {
            throw new AccessDeniedException("当前账号无权访问该机构批次");
        }
        if (batch.category() != MasterDataCategory.HOSPITAL_DIRECTORY) {
            throw new InvalidRequestException("当前批次不是100-003医院综合目录业务");
        }
        return batch;
    }

    /**
     * 读取机构范围批次的机构主键。
     *
     * @param organizationCode 平台机构代码
     * @return 启用机构主键
     */
    private long requireOrganizationId(String organizationCode) {
        Long organizationId = batchMapper.findEnabledOrganizationId(organizationCode);
        if (organizationId == null) throw new InvalidRequestException("批次机构不存在或已停用");
        return organizationId;
    }

    /**
     * 返回医院综合目录类型的中文名称。
     *
     * @param type HIS目录类型
     * @return 面向业务的名称
     */
    private String typeLabel(HospitalDirectoryType type) {
        return switch (type) {
            case DEPARTMENT -> "科室";
            case DOCTOR -> "医生";
            case WARD -> "病区";
            case BED -> "床位";
        };
    }

    /**
     * 裁剪HIS可选文本并把空白统一为无值。
     *
     * @param value 原始可选文本
     * @return 去除空白后的值
     */
    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 判断可选文本是否超过数据库字段长度。
     *
     * @param value 可选文本
     * @param maximum 最大长度
     * @return 是否超长
     */
    private boolean exceeds(String value, int maximum) {
        String normalized = normalize(value);
        return normalized != null && normalized.length() > maximum;
    }

    /**
     * 生成不含地址、凭证和报文正文的外部失败摘要。
     *
     * @param message 外部失败文本
     * @return 不超过数据库限制的可理解文本
     */
    private String safeFailure(String message) {
        String value = normalize(message);
        if (value == null) return "基层HIS未返回可理解的失败原因";
        if (value.contains("无权访问") || value.contains("授权已过期") || value.contains("申请机构授权")) {
            return "基层HIS未授予该机构100-003医院综合目录查询权限";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    /**
     * 生成单行且长度受控的本地异常摘要。
     *
     * @param exception 本地对账异常
     * @return 不含换行且长度受控的单行技术原因
     */
    private String technicalFailure(RuntimeException exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = normalize(cause.getMessage());
        if (message == null) return cause.getClass().getSimpleName();
        String singleLine = message.replace('\r', ' ').replace('\n', ' ');
        if (singleLine.length() > 300) singleLine = singleLine.substring(0, 300);
        return cause.getClass().getSimpleName() + "：" + singleLine;
    }

    /** 100-003四类结果的自动校验事实。 */
    private record ValidationResult(long returned, long duplicates, long invalid, long conflicts,
                                    List<HospitalDirectorySourceRecord> records,
                                    List<HospitalDirectoryRelationRecord> relations, boolean valid,
                                    String failureSummary) {
    }

    /** 单一目录类型直接对账后的本地变更数量。 */
    private record DirectoryMutationCounts(long created, long updated, long unchanged, long sourceMissing) {
    }

    /** 单一目录类型的外部调用、校验和写入结果。 */
    private record TypeSyncOutcome(HospitalDirectoryType type, boolean completed, boolean unknown, long returned,
                                   String failureSummary, ValidationResult validation,
                                   DirectoryMutationCounts counts) {

        /**
         * 创建单一目录类型完成对账后的处理结果。
         *
         * @param type 目录类型
         * @param validation 校验结果
         * @param counts 写入结果
         * @return 已完成对账结果
         */
        private static TypeSyncOutcome completed(HospitalDirectoryType type, ValidationResult validation,
                                                 DirectoryMutationCounts counts) {
            return new TypeSyncOutcome(type, true, false, validation.returned(), null, validation, counts);
        }

        /**
         * 创建单一目录类型失败或结果未知的处理结果。
         *
         * @param type 目录类型
         * @param unknown 是否无法确认上游结果
         * @param returned 已确认收到的记录数
         * @param summary 受控失败摘要
         * @return 未完成结果
         */
        private static TypeSyncOutcome failed(HospitalDirectoryType type, boolean unknown, long returned,
                                              String summary) {
            return failed(type, unknown, returned, summary, null);
        }

        /**
         * 创建单一目录类型失败或结果未知的处理结果。
         *
         * @param type 目录类型
         * @param unknown 是否无法确认上游结果
         * @param returned 已确认收到的记录数
         * @param summary 受控失败摘要
         * @param validation 可选校验结果
         * @return 未完成结果
         */
        private static TypeSyncOutcome failed(HospitalDirectoryType type, boolean unknown, long returned,
                                              String summary, ValidationResult validation) {
            return new TypeSyncOutcome(type, false, unknown, returned, summary, validation, null);
        }
    }
}
