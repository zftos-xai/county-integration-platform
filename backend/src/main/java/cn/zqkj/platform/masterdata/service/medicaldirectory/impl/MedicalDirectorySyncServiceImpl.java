package cn.zqkj.platform.masterdata.service.medicaldirectory.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;
import cn.zqkj.platform.his.exception.PhisBusinessException;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisConfigurationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryFetchResult;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySyncResult;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySyncResultStatus;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryValidationResult;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper;
import cn.zqkj.platform.masterdata.mapper.medicaldirectory.MedicalDirectorySyncMapper;
import cn.zqkj.platform.masterdata.service.batch.MasterDataBatchService;
import cn.zqkj.platform.masterdata.service.medicaldirectory.MedicalDirectoryFetchService;
import cn.zqkj.platform.masterdata.service.medicaldirectory.MedicalDirectorySyncService;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 将100-005数量核对和100-004分页取得的医疗目录按类型独立更新为当前数据。
 *
 * <p>接口资料尚未证明时间范围未返回代表来源删除，因此本实现只新增、更新和恢复有效记录；
 * 不会以增量查询结果标记历史记录无效。外部HIS调用永远位于本地事务之外。</p>
 */
@Service
public class MedicalDirectorySyncServiceImpl implements MedicalDirectorySyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MedicalDirectorySyncServiceImpl.class);
    private final MedicalDirectorySyncMapper mapper;
    private final MasterDataBatchMapper batchMapper;
    private final MasterDataBatchService batchService;
    private final MedicalDirectoryFetchService fetchService;
    private final ManagementAuditService auditService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建医疗目录同步服务。
     *
     * @param mapper 医疗目录当前数据和结果持久化边界
     * @param batchMapper 同步批次读取边界
     * @param batchService 权限内批次读取服务
     * @param fetchService 100-005和100-004完整取数服务
     * @param auditService 管理审计服务
     * @param transactionTemplate 短事务模板
     */
    public MedicalDirectorySyncServiceImpl(
            MedicalDirectorySyncMapper mapper,
            MasterDataBatchMapper batchMapper,
            MasterDataBatchService batchService,
            MedicalDirectoryFetchService fetchService,
            ManagementAuditService auditService,
            TransactionTemplate transactionTemplate
    ) {
        this.mapper = mapper;
        this.batchMapper = batchMapper;
        this.batchService = batchService;
        this.fetchService = fetchService;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * {@inheritDoc}
     *
     * <p>每一类型都先通过100-005与100-004的同范围数量核对。某一类型失败或结果未知时，
     * 只保留该类型旧数据，不影响其他完整成功类型。</p>
     */
    @Override
    public MasterDataBatchSummaryVO fetchValidateAndReconcile(long batchId, byte[] expectedVersion, AccessActor actor) {
        MasterDataBatchSnapshot batch = requireMedicalDirectoryBatch(batchId, actor);
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
        for (MedicalDirectoryType type : MedicalDirectoryType.values()) {
            outcomes.add(fetchValidateAndSynchronize(batchId, organizationId, batch, type));
        }
        finishRun(batchId, organizationId, batch, outcomes, actor);
        return batchService.get(batchId, actor);
    }

    /**
     * 完整处理一种医疗目录；获取、校验和保存异常都限制在当前类型内。
     *
     * @param batchId 同步批次主键
     * @param organizationId 平台机构主键
     * @param batch 受控批次范围
     * @param type 当前医疗目录类型
     * @return 当前类型的处理结论
     */
    private TypeSyncOutcome fetchValidateAndSynchronize(
            long batchId, long organizationId, MasterDataBatchSnapshot batch, MedicalDirectoryType type
    ) {
        MedicalDirectoryValidationResult validation;
        try {
            MedicalDirectoryFetchResult result = fetchService.fetchAll(
                    organizationId, batch.environment(), type, batch.rangeStart(), batch.rangeEnd(),
                    batch.sourceOrganizationId());
            validation = result.validation();
        } catch (PhisCommunicationException | PhisProtocolException exception) {
            return TypeSyncOutcome.unknown(type,
                    "未能确认" + type.displayName() + "目录查询结果，请先确认HIS交易记录后再发起新的同步");
        } catch (PhisConfigurationException | PhisBusinessException exception) {
            return TypeSyncOutcome.failed(type, safeFailure(exception.getMessage()));
        } catch (RuntimeException exception) {
            LOGGER.error("100-004/100-005医疗目录取得失败｜批次{}｜类型{}｜{}", batchId, type.code(),
                    technicalFailure(exception));
            return TypeSyncOutcome.failed(type, "平台未能完成" + type.displayName() + "目录取得，当前数据未改变");
        }
        if (!validation.valid()) {
            return TypeSyncOutcome.failed(type, validation, validation.failureSummary());
        }
        try {
            MutationCounts counts = transactionTemplate.execute(ignored -> synchronizeType(
                    batchId, organizationId, validation.acceptedRecords()));
            return TypeSyncOutcome.completed(type, validation, counts);
        } catch (RuntimeException exception) {
            LOGGER.error("100-004医疗目录保存失败｜批次{}｜类型{}｜{}", batchId, type.code(),
                    technicalFailure(exception));
            return TypeSyncOutcome.failed(type, validation,
                    "平台保存" + type.displayName() + "目录失败，当前数据未改变");
        }
    }

    /**
     * 在短事务内直接更新完整且自动校验通过的当前医疗目录。
     *
     * <p>不调用缺失失效逻辑：100-004的时间范围未被资料证明为全量快照。</p>
     *
     * @param batchId 同步批次主键
     * @param organizationId 平台机构主键
     * @param records 已通过自动校验的唯一来源记录
     * @return 本次当前数据变更数量
     */
    private MutationCounts synchronizeType(long batchId, long organizationId, List<MedicalDirectorySourceRecord> records) {
        long created = 0;
        long updated = 0;
        long unchanged = 0;
        for (MedicalDirectorySourceRecord record : records) {
            int classification = mapper.classifyDirectory(organizationId, record);
            if (classification == 0) {
                if (mapper.insertDirectoryDirect(batchId, organizationId, record) == 1) {
                    created++;
                } else {
                    int currentClassification = mapper.classifyDirectory(organizationId, record);
                    mapper.updateDirectoryDirect(batchId, organizationId, record);
                    if (currentClassification == 1) updated++;
                    else unchanged++;
                }
            } else {
                mapper.updateDirectoryDirect(batchId, organizationId, record);
                if (classification == 1) updated++;
                else unchanged++;
            }
        }
        return new MutationCounts(created, updated, unchanged);
    }

    /**
     * 汇总所有医疗目录类型结果，并在同一短事务中落库结果、结束批次和追加审计。
     *
     * @param batchId 同步批次主键
     * @param organizationId 平台机构主键
     * @param batch 批次范围快照
     * @param outcomes 每类医疗目录的处理结论
     * @param actor 发起同步的当前用户
     */
    private void finishRun(long batchId, long organizationId, MasterDataBatchSnapshot batch,
                           List<TypeSyncOutcome> outcomes, AccessActor actor) {
        long returned = outcomes.stream().mapToLong(TypeSyncOutcome::returned).sum();
        long duplicates = outcomes.stream().mapToLong(outcome -> number(outcome.validation(), MedicalDirectoryValidationResult::duplicateCount)).sum();
        long invalid = outcomes.stream().mapToLong(outcome -> number(outcome.validation(), MedicalDirectoryValidationResult::invalidCount)).sum();
        long conflicts = outcomes.stream().mapToLong(outcome -> number(outcome.validation(), MedicalDirectoryValidationResult::conflictCount)).sum();
        Long declared = outcomes.stream().allMatch(outcome -> outcome.validation() != null)
                ? outcomes.stream().mapToLong(outcome -> outcome.validation().declaredCount()).sum()
                : null;
        long created = outcomes.stream().mapToLong(outcome -> outcome.counts() == null ? 0 : outcome.counts().created()).sum();
        long updated = outcomes.stream().mapToLong(outcome -> outcome.counts() == null ? 0 : outcome.counts().updated()).sum();
        long unchanged = outcomes.stream().mapToLong(outcome -> outcome.counts() == null ? 0 : outcome.counts().unchanged()).sum();
        boolean unknown = outcomes.stream().anyMatch(TypeSyncOutcome::unknown);
        boolean allCompleted = outcomes.stream().allMatch(TypeSyncOutcome::completed);
        String status = allCompleted ? "COMPLETED" : unknown ? "COMPLETED_WITH_UNKNOWN" : "COMPLETED_WITH_ERRORS";
        String failureCode = allCompleted ? null : unknown ? "HIS_PARTIAL_RESULT_UNKNOWN" : "HIS_PARTIAL_FAILURE";
        String summary = allCompleted ? null : summarizeFailures(outcomes);
        ManagementAuditCommand audit = new ManagementAuditCommand(
                actor, null, organizationId, batch.organizationCode(), "MASTER_DATA_MEDICAL_DIRECTORY_SYNCED",
                "MASTER_DATA_BATCH", batch.batchNo(), allCompleted ? "SUCCESS" : "PARTIAL",
                "100-004/100-005目录直接同步：" + created + "新增，" + updated + "更新，" + unchanged + "未变化", null);
        transactionTemplate.executeWithoutResult(ignored -> {
            outcomes.forEach(outcome -> mapper.saveDirectoryResult(batchId, toResult(organizationId, outcome)));
            long active = mapper.countActive(organizationId);
            if (mapper.finishCompleted(batchId, status, declared, returned, duplicates, invalid, conflicts, created,
                    updated, unchanged, 0, active, failureCode, summary, actor.loginName()) != 1) {
                throw new ResourceConflictException("同步运行状态已经变化，请重新读取后再操作");
            }
            auditService.recordSuccess(audit);
        });
    }

    /**
     * 将运行期结果转为可持久化的单类型事实。
     *
     * @param organizationId 平台机构主键
     * @param outcome 当前类型处理结论
     * @return 可复核的分项运行事实
     */
    private MedicalDirectorySyncResult toResult(long organizationId, TypeSyncOutcome outcome) {
        MedicalDirectoryValidationResult validation = outcome.validation();
        MutationCounts counts = outcome.counts();
        MedicalDirectorySyncResultStatus status = outcome.completed() ? MedicalDirectorySyncResultStatus.COMPLETED
                : outcome.unknown() ? MedicalDirectorySyncResultStatus.RESULT_UNKNOWN
                : MedicalDirectorySyncResultStatus.FAILED;
        return new MedicalDirectorySyncResult(
                outcome.type(), status, validation == null ? null : validation.declaredCount(), outcome.returned(),
                number(validation, MedicalDirectoryValidationResult::duplicateCount),
                number(validation, MedicalDirectoryValidationResult::invalidCount),
                number(validation, MedicalDirectoryValidationResult::conflictCount),
                counts == null ? 0 : counts.created(), counts == null ? 0 : counts.updated(),
                counts == null ? 0 : counts.unchanged(), 0,
                outcome.completed() ? mapper.countActiveByType(organizationId, outcome.type()) : null,
                outcome.completed() ? null : outcome.failureSummary());
    }

    /**
     * 提取可选校验结果中的一个数量字段。
     *
     * @param validation 可选校验结果
     * @param extractor 数量字段读取器
     * @return 无校验结果时为零
     */
    private long number(MedicalDirectoryValidationResult validation,
                        java.util.function.ToLongFunction<MedicalDirectoryValidationResult> extractor) {
        return validation == null ? 0 : extractor.applyAsLong(validation);
    }

    /**
     * 读取并校验可执行的医疗目录同步批次。
     *
     * @param batchId 同步批次主键
     * @param actor 当前操作人
     * @return 有权限且范围完整的医疗目录批次
     */
    private MasterDataBatchSnapshot requireMedicalDirectoryBatch(long batchId, AccessActor actor) {
        MasterDataBatchSnapshot batch = batchMapper.findById(batchId);
        if (batch == null) throw new InvalidRequestException("同步批次不存在");
        if (batch.organizationCode() == null || !actor.canAccess(batch.organizationCode())) {
            throw new AccessDeniedException("当前账号无权访问该机构批次");
        }
        if (batch.category() != MasterDataCategory.MEDICAL_DIRECTORY) {
            throw new InvalidRequestException("当前批次不是100-004/100-005医疗目录业务");
        }
        if (batch.rangeStart() == null || batch.rangeEnd() == null || batch.sourceOrganizationId() == null) {
            throw new InvalidRequestException("医疗目录同步批次缺少来源机构或查询时间范围");
        }
        return batch;
    }

    /**
     * 读取运行批次对应的当前启用机构主键。
     *
     * @param organizationCode 平台机构编码
     * @return 当前启用机构主键
     */
    private long requireOrganizationId(String organizationCode) {
        Long organizationId = batchMapper.findEnabledOrganizationId(organizationCode);
        if (organizationId == null) throw new InvalidRequestException("批次机构不存在或已停用");
        return organizationId;
    }

    /**
     * 合并未完成类型的受控失败原因。
     *
     * @param outcomes 各类型处理结论
     * @return 不超过数据库限制的摘要
     */
    private String summarizeFailures(List<TypeSyncOutcome> outcomes) {
        String summary = outcomes.stream().filter(outcome -> !outcome.completed())
                .map(outcome -> outcome.type().displayName() + "：" + outcome.failureSummary())
                .reduce((left, right) -> left + "；" + right).orElse("部分医疗目录未完成同步");
        return safeFailure(summary);
    }

    /**
     * 生成不含连接地址、凭证或报文正文的来源失败摘要。
     *
     * @param message 上游受控错误
     * @return 可安全展示和保存的短摘要
     */
    private String safeFailure(String message) {
        if (message == null || message.isBlank()) return "基层HIS未返回可理解的失败原因";
        if (message.contains("无权访问") || message.contains("授权已过期") || message.contains("申请机构授权")) {
            return "基层HIS未授予该机构100-004/100-005医疗目录查询权限";
        }
        String singleLine = message.replace('\r', ' ').replace('\n', ' ').trim();
        return singleLine.length() <= 500 ? singleLine : singleLine.substring(0, 500);
    }

    /**
     * 生成单行受控本地异常摘要，仅用于服务端日志。
     *
     * @param exception 当前异常
     * @return 异常类别和简短原因
     */
    private String technicalFailure(RuntimeException exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) return cause.getClass().getSimpleName();
        String singleLine = message.replace('\r', ' ').replace('\n', ' ').trim();
        return cause.getClass().getSimpleName() + "：" + (singleLine.length() <= 300 ? singleLine : singleLine.substring(0, 300));
    }

    /** 单一医疗目录直接同步后的当前数据变更数量。 */
    private record MutationCounts(long created, long updated, long unchanged) {
    }

    /** 单一医疗目录类型的外部调用、校验和当前数据更新结论。 */
    private record TypeSyncOutcome(
            MedicalDirectoryType type,
            boolean completed,
            boolean unknown,
            long returned,
            String failureSummary,
            MedicalDirectoryValidationResult validation,
            MutationCounts counts
    ) {

        /** 创建一项完成直接同步的结论。 */
        private static TypeSyncOutcome completed(MedicalDirectoryType type,
                                                 MedicalDirectoryValidationResult validation,
                                                 MutationCounts counts) {
            return new TypeSyncOutcome(type, true, false, validation.returnedCount(), null, validation, counts);
        }

        /** 创建一项明确失败的结论。 */
        private static TypeSyncOutcome failed(MedicalDirectoryType type, String summary) {
            return new TypeSyncOutcome(type, false, false, 0, summary, null, null);
        }

        /** 创建一项已取得但校验失败的结论。 */
        private static TypeSyncOutcome failed(MedicalDirectoryType type,
                                              MedicalDirectoryValidationResult validation, String summary) {
            return new TypeSyncOutcome(type, false, false, validation.returnedCount(), summary, validation, null);
        }

        /** 创建一项结果未知的结论。 */
        private static TypeSyncOutcome unknown(MedicalDirectoryType type, String summary) {
            return new TypeSyncOutcome(type, false, true, 0, summary, null, null);
        }
    }
}
