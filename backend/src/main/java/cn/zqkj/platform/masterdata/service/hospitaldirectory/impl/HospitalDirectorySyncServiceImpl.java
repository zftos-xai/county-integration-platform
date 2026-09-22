package cn.zqkj.platform.masterdata.service.hospitaldirectory.impl;

import cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCounts;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.his.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryEntry;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisBusinessException;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisConfigurationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryRelationRecord;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectorySyncResultVO;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectorySyncResultStatus;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryType;
import cn.zqkj.platform.masterdata.mapper.hospitaldirectory.HospitalDirectorySyncMapper;
import cn.zqkj.platform.masterdata.service.hospitaldirectory.HospitalDirectorySyncService;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PhisService phisService;
    private final ManagementAuditService auditService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建医院综合目录同步服务并注入批次、HIS和持久化依赖。
     *
     * @param batchMapper 批次执行版本锁
     * @param mapper 综合目录持久化边界
     * @param phisService HIS强类型调用服务
     * @param auditService 管理审计服务
     * @param transactionTemplate 短事务模板
     */
    public HospitalDirectorySyncServiceImpl(HospitalDirectorySyncMapper mapper, PhisService phisService,
                                            ManagementAuditService auditService, TransactionTemplate transactionTemplate,
                                            MasterDataBatchMapper batchMapper) {
        this.mapper = mapper;
        this.batchMapper = batchMapper;
        this.phisService = phisService;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 同步科室、病区、床位和医生目录，并保留每种类型的真实处理结果。
     *
     * <p>处理已由批次用例抢占的批次，按依赖顺序在事务外调用HIS；每个完整通过校验的目录类型
     * 独立短事务落库。失败或结果未知的类型保留原有效数据，避免把不完整返回误判为删除。</p>
     */
    @Override
    public void synchronize(MasterDataBatchSnapshot batch, Long actorUserId, String actorLogin) {
        Set<HospitalDirectoryType> synchronizedTypes = new HashSet<>();
        for (HospitalDirectoryType type : syncOrder()) {
            TypeSyncOutcome outcome = fetchValidateAndSynchronize(batch, type, synchronizedTypes);
            if (outcome.completed()) {
                synchronizedTypes.add(type);
            } else {
                transactionTemplate.executeWithoutResult(ignored -> {
                    requireExecution(batch);
                    mapper.saveDirectoryResult(batch.id(), toDirectoryResult(batch.organizationId(), outcome));
                });
            }
        }
        completeRecordedResults(batch, actorUserId, actorLogin);
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
     * 取得、校验并对账一个目录类型。来源或对账失败只影响该类型；执行权失效则立即终止整个运行。
     *
     * @param batch 运行范围快照
     * @param type 当前目录类型
     * @param synchronizedTypes 本次已经完整成功的目录类型
     * @return 当前类型的可追溯处理结果
     */
    private TypeSyncOutcome fetchValidateAndSynchronize(MasterDataBatchSnapshot batch,
                                                         HospitalDirectoryType type,
                                                         Set<HospitalDirectoryType> synchronizedTypes) {
        long batchId = batch.id();
        long organizationId = batch.organizationId();
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
            return transactionTemplate.execute(ignored -> {
                requireExecution(batch);
                DirectoryMutationCounts counts = synchronizeType(batchId, organizationId, type, validation);
                TypeSyncOutcome outcome = TypeSyncOutcome.completed(type, validation, counts);
                mapper.saveDirectoryResult(batchId, toDirectoryResult(organizationId, outcome));
                return outcome;
            });
        } catch (ResourceConflictException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error("100-003目录对账失败｜批次{}｜类型{}｜{}", batchId, type.code(), Func.rootCause(exception).getClass().getSimpleName());
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
        long returned = 0;
        long duplicates = 0;
        long invalid = 0;
        long conflicts = 0;
        Set<HospitalDirectoryRelationRecord> relations = new HashSet<>();
        Set<String> rejected = new HashSet<>();
        for (HospitalDirectoryEntry entry : entries) {
            returned++;
            HospitalDirectorySourceRecord record = toRecord(type, entry);
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
            }
        }
        boolean valid = invalid == 0 && conflicts == 0;
        String summary = valid ? null : "来源记录无效" + invalid + "条，编码冲突" + conflicts + "组";
        boolean relationsComplete = switch (type) {
            case DEPARTMENT -> true;
            case WARD -> synchronizedTypes.contains(HospitalDirectoryType.DEPARTMENT);
            case DOCTOR, BED -> synchronizedTypes.contains(HospitalDirectoryType.WARD);
        };
        return new ValidationResult(returned, duplicates, invalid, conflicts, List.copyOf(accepted.values()), List.copyOf(relations),
                valid, relationsComplete, summary);
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
     * @return 可持久化的非敏感记录；字段无效时为空
     */
    private HospitalDirectorySourceRecord toRecord(HospitalDirectoryType type, HospitalDirectoryEntry entry) {
        if (entry == null) {
            return null;
        }
        String code = Func.trimToNull(entry.directoryCode());
        String name = Func.trimToNull(entry.directoryName());
        if (code == null || name == null || Func.exceedsTrimmedLength(code, 50)
                || Func.exceedsTrimmedLength(name, 50)
                || Func.exceedsTrimmedLength(entry.mnemonicCode(), 20)
                || Func.exceedsTrimmedLength(entry.categoryName(), 20)
                || Func.exceedsTrimmedLength(entry.remark(), 100)
                || Func.exceedsTrimmedLength(entry.departmentCode(), 50)
                || Func.exceedsTrimmedLength(entry.departmentName(), 50)
                || Func.exceedsTrimmedLength(entry.ward(), 50)
                || Func.exceedsTrimmedLength(entry.organizationCode(), 50)) {
            return null;
        }
        return new HospitalDirectorySourceRecord(type, code, name, Func.trimToNull(entry.mnemonicCode()),
                Func.trimToNull(entry.categoryName()), Func.trimToNull(entry.remark()),
                Func.trimToNull(entry.departmentCode()), Func.trimToNull(entry.departmentName()),
                Func.trimToNull(entry.ward()), Func.trimToNull(entry.organizationCode()));
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
        if (result.relationsComplete()) {
            mapper.deleteRelationsForType(organizationId, type);
            for (HospitalDirectoryRelationRecord relation : result.relations()) {
                mapper.insertRelationDirect(batchId, organizationId, relation);
            }
        }
        return new DirectoryMutationCounts(created, updated, unchanged, sourceMissing);
    }

    /**
     * 只依据已落库分项结束批次，不调用HIS；中断后未保存的类型明确记为结果未知。
     *
     * <p>持有执行版本锁直至状态与审计提交，旧执行者随后不能继续落库。恢复结束后如需重取，
     * 必须由操作人核查后创建新批次，不能复用旧批次号覆盖原运行事实。</p>
     * @param batch 当前执行版本快照
     * @param actorUserId 审计用户主键；内部任务可为空
     * @param actorLogin 操作人或任务名称
     */
    @Override
    public void completeRecordedResults(MasterDataBatchSnapshot batch, Long actorUserId, String actorLogin) {
        transactionTemplate.executeWithoutResult(ignored -> {
            requireExecution(batch);
            List<HospitalDirectorySyncResultVO> results = mapper.findResults(batch.id());
            long returned = 0, duplicates = 0, invalid = 0, conflicts = 0;
            long created = 0, updated = 0, unchanged = 0, sourceMissing = 0;

            boolean unknown = results.size() != HospitalDirectoryType.values().length;
            boolean allCompleted = !unknown;
            for (HospitalDirectorySyncResultVO result : results) {
                returned += result.returnedCount();
                duplicates += result.duplicateCount();
                invalid += result.invalidCount();
                conflicts += result.conflictCount();
                created += result.createdCount();
                updated += result.updatedCount();
                unchanged += result.unchangedCount();
                sourceMissing += result.sourceMissingCount();

                unknown |= result.status() == HospitalDirectorySyncResultStatus.RESULT_UNKNOWN;
                allCompleted &= result.status() == HospitalDirectorySyncResultStatus.COMPLETED;
            }
            String status = allCompleted ? "COMPLETED" : unknown ? "COMPLETED_WITH_UNKNOWN" : "COMPLETED_WITH_ERRORS";
            String failureCode = allCompleted ? null : unknown ? "HIS_PARTIAL_RESULT_UNKNOWN" : "HIS_PARTIAL_FAILURE";
            String summary = allCompleted ? null : summarizeFailures(results);
            MasterDataBatchCounts counts = new MasterDataBatchCounts(null,
                    returned, duplicates, invalid, conflicts, created, updated, unchanged, sourceMissing,
                    mapper.countActive(batch.organizationId()));
            if (mapper.finishCompleted(batch.id(), status, counts, failureCode, summary, actorLogin) != 1) {
                throw new ResourceConflictException("同步运行状态已经变化，请重新读取后再操作");
            }
            auditService.append(new ManagementAuditCommand(actorUserId, actorLogin, batch.organizationId(),
                    batch.organizationCode(), "MASTER_DATA_HOSPITAL_DIRECTORY_SYNCED", "MASTER_DATA_BATCH",
                    batch.batchNo(), allCompleted ? "SUCCESS" : "FAILURE",
                    "医院综合目录批次结束；已保存分项" + results.size() + "类，新增" + created + "条，更新" + updated + "条",
                    null));
        });
    }

    /** 在每个写事务开始时确认执行权；恢复或结束批次后拒绝旧执行者继续写入。 */
    private void requireExecution(MasterDataBatchSnapshot batch) {
        if (!batchMapper.lockExecution(batch.id(), batch.version())) {
            throw new ResourceConflictException("批次执行权已失效，请重新读取批次");
        }
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
    private HospitalDirectorySyncResultVO toDirectoryResult(long organizationId, TypeSyncOutcome outcome) {
        ValidationResult validation = outcome.validation();
        DirectoryMutationCounts counts = outcome.counts();
        HospitalDirectorySyncResultStatus status = outcome.completed()
                ? HospitalDirectorySyncResultStatus.COMPLETED
                : outcome.unknown() ? HospitalDirectorySyncResultStatus.RESULT_UNKNOWN
                : HospitalDirectorySyncResultStatus.FAILED;
        return new HospitalDirectorySyncResultVO(
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
    private String summarizeFailures(List<HospitalDirectorySyncResultVO> outcomes) {
        StringJoiner failures = new StringJoiner("；");
        for (HospitalDirectorySyncResultVO outcome : outcomes) {
            if (outcome.status() != HospitalDirectorySyncResultStatus.COMPLETED) {
                failures.add(typeLabel(outcome.directoryType()) + "：" + outcome.failureSummary());
            }
        }
        if (outcomes.size() != HospitalDirectoryType.values().length) failures.add("运行中断，未保存类型结果未知");
        return failures.length() == 0 ? "部分目录未完成同步" : Func.substring(failures.toString(), 0, 500);
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
     * 生成不含地址、凭证和报文正文的外部失败摘要。
     *
     * @param message 外部失败文本
     * @return 不超过数据库限制的可理解文本
     */
    private String safeFailure(String message) {
        String value = Func.trimToNull(message);
        if (value == null) return "基层HIS未返回可理解的失败原因";
        if (value.contains("无权访问") || value.contains("授权已过期") || value.contains("申请机构授权")) {
            return "基层HIS未授予该机构100-003医院综合目录查询权限";
        }
        return "基层HIS拒绝目录查询，请核查来源系统配置与交易记录";
    }

    /** 100-003四类结果的自动校验事实。 */
    private record ValidationResult(long returned, long duplicates, long invalid, long conflicts,
                                    List<HospitalDirectorySourceRecord> records,
                                    List<HospitalDirectoryRelationRecord> relations, boolean valid,
                                    boolean relationsComplete, String failureSummary) {
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
