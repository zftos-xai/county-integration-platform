package cn.zqkj.platform.masterdata.service.medicaldirectory.impl;

import cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCounts;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.his.exception.PhisBusinessException;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisConfigurationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectorySyncResultVO;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySyncResultStatus;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryValidationResult;
import cn.zqkj.platform.masterdata.mapper.medicaldirectory.MedicalDirectorySyncMapper;
import cn.zqkj.platform.masterdata.service.medicaldirectory.MedicalDirectoryFetchService;
import cn.zqkj.platform.masterdata.service.medicaldirectory.MedicalDirectorySyncService;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final MedicalDirectoryFetchService fetchService;
    private final ManagementAuditService auditService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建医疗目录同步服务。
     *
     * @param batchMapper 批次执行版本锁
     * @param mapper 医疗目录当前数据和结果持久化边界
     * @param fetchService 100-005和100-004完整取数服务
     * @param auditService 管理审计服务
     * @param transactionTemplate 短事务模板
     */
    public MedicalDirectorySyncServiceImpl(
            MedicalDirectorySyncMapper mapper,
            MedicalDirectoryFetchService fetchService,
            ManagementAuditService auditService,
            TransactionTemplate transactionTemplate,
            MasterDataBatchMapper batchMapper
    ) {
        this.mapper = mapper;
        this.batchMapper = batchMapper;
        this.fetchService = fetchService;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 同步药品、诊疗和耗材目录，并保留每种类型的真实处理结果。
     *
     * <p>每一类型都先通过100-005与100-004的同范围数量核对。某一类型失败或结果未知时，
     * 只保留该类型旧数据，不影响其他完整成功类型。</p>
     */
    @Override
    public void synchronize(MasterDataBatchSnapshot batch, Long actorUserId, String actorLogin) {
        for (MedicalDirectoryType type : MedicalDirectoryType.values()) {
            TypeSyncOutcome outcome = fetchValidateAndSynchronize(batch, type);
            if (!outcome.completed()) {
                transactionTemplate.executeWithoutResult(ignored -> {
                    requireExecution(batch);
                    mapper.saveDirectoryResult(batch.id(), toResult(batch.organizationId(), outcome));
                });
            }
        }
        completeRecordedResults(batch, actorUserId, actorLogin);
    }

    /**
     * 完整处理一种医疗目录；来源或保存失败仅影响该类型，执行权失效则终止整个运行。
     *
     * @param batch 受控批次范围
     * @param type 当前医疗目录类型
     * @return 当前类型的处理结论
     */
    private TypeSyncOutcome fetchValidateAndSynchronize(
            MasterDataBatchSnapshot batch, MedicalDirectoryType type
    ) {
        long batchId = batch.id();
        long organizationId = batch.organizationId();
        MedicalDirectoryValidationResult validation;
        try {
            validation = fetchService.fetchAll(batch, type);
        } catch (PhisCommunicationException | PhisProtocolException exception) {
            return TypeSyncOutcome.unknown(type, exception.getMessage());
        } catch (PhisBusinessException exception) {
            return TypeSyncOutcome.failed(type, exception.getMessage());
        } catch (PhisConfigurationException exception) {
            return TypeSyncOutcome.failed(type, "HIS接口配置不可用，未发送目录查询；请检查已启用的机构接口配置");
        } catch (RuntimeException exception) {
            LOGGER.error("100-004/100-005医疗目录取得失败｜批次{}｜类型{}｜{}", batchId, type.code(),
                    Func.rootCause(exception).getClass().getSimpleName());
            return TypeSyncOutcome.failed(type, "平台未能完成" + type.displayName() + "目录取得，当前数据未改变");
        }
        if (!validation.valid()) {
            return TypeSyncOutcome.failed(type, validation, validation.failureSummary());
        }
        try {
            return transactionTemplate.execute(ignored -> {
                requireExecution(batch);
                if (batchMapper.hasBoundSourceChanged(batch.id())) {
                    throw new ResourceConflictException("目录取得期间HIS端点配置已改变，该类型未更新当前目录");
                }
                MutationCounts counts = synchronizeType(batchId, organizationId, validation.acceptedRecords());
                TypeSyncOutcome outcome = TypeSyncOutcome.completed(type, validation, counts);
                mapper.saveDirectoryResult(batchId, toResult(organizationId, outcome));
                return outcome;
            });
        } catch (ResourceConflictException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error("100-004医疗目录保存失败｜批次{}｜类型{}｜{}", batchId, type.code(),
                    Func.rootCause(exception).getClass().getSimpleName());
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
                    if (mapper.updateDirectoryDirect(batchId, organizationId, record) != 1) {
                        throw new IllegalStateException("目录更新未命中唯一记录");
                    }
                    if (currentClassification == 1) updated++;
                    else unchanged++;
                }
            } else {
                if (mapper.updateDirectoryDirect(batchId, organizationId, record) != 1) {
                    throw new IllegalStateException("目录更新未命中唯一记录");
                }
                if (classification == 1) updated++;
                else unchanged++;
            }
        }
        return new MutationCounts(created, updated, unchanged);
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
            List<MedicalDirectorySyncResultVO> results = mapper.findResults(batch.id());
            long returned = 0, duplicates = 0, invalid = 0, conflicts = 0;
            long created = 0, updated = 0, unchanged = 0, sourceMissing = 0;
            long declaredTotal = 0;
            boolean declaredKnown = results.size() == MedicalDirectoryType.values().length;
            boolean unknown = results.size() != MedicalDirectoryType.values().length;
            boolean allCompleted = !unknown;
            int completedTypes = 0;
            for (MedicalDirectorySyncResultVO result : results) {
                returned += result.returnedCount();
                duplicates += result.duplicateCount();
                invalid += result.invalidCount();
                conflicts += result.conflictCount();
                created += result.createdCount();
                updated += result.updatedCount();
                unchanged += result.unchangedCount();
                sourceMissing += result.sourceMissingCount();
                if (result.declaredCount() == null) declaredKnown = false;
                else declaredTotal += result.declaredCount();
                unknown |= result.status() == MedicalDirectorySyncResultStatus.RESULT_UNKNOWN;
                allCompleted &= result.status() == MedicalDirectorySyncResultStatus.COMPLETED;
                if (result.status() == MedicalDirectorySyncResultStatus.COMPLETED) completedTypes++;
            }
            String status = allCompleted ? "COMPLETED" : unknown ? "COMPLETED_WITH_UNKNOWN"
                    : completedTypes == 0 ? "FAILED" : "COMPLETED_WITH_ERRORS";
            String failureCode = allCompleted ? null : unknown ? "HIS_PARTIAL_RESULT_UNKNOWN"
                    : completedTypes == 0 ? "HIS_ALL_TYPES_FAILED" : "HIS_PARTIAL_FAILURE";
            String summary = allCompleted ? null : summarizeFailures(results);
            MasterDataBatchCounts counts = new MasterDataBatchCounts(declaredKnown ? declaredTotal : null,
                    returned, duplicates, invalid, conflicts, created, updated, unchanged, sourceMissing,
                    "FAILED".equals(status) ? null : mapper.countActive(batch.organizationId()));
            if (mapper.finishCompleted(batch.id(), status, counts, failureCode, summary, actorLogin) != 1) {
                throw new ResourceConflictException("同步运行状态已经变化，请重新读取后再操作");
            }
            auditService.append(new ManagementAuditCommand(actorUserId, actorLogin, batch.organizationId(),
                    batch.organizationCode(), "MASTER_DATA_MEDICAL_DIRECTORY_SYNCED", "MASTER_DATA_BATCH",
                    batch.batchNo(), allCompleted ? "SUCCESS" : "FAILURE",
                    "医疗目录批次结束；已保存分项" + results.size() + "类，新增" + created + "条，更新" + updated + "条",
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
     * 将运行期结果转为可持久化的单类型事实。
     *
     * @param organizationId 平台机构主键
     * @param outcome 当前类型处理结论
     * @return 可复核的分项运行事实
     */
    private MedicalDirectorySyncResultVO toResult(long organizationId, TypeSyncOutcome outcome) {
        MedicalDirectoryValidationResult validation = outcome.validation();
        MutationCounts counts = outcome.counts();
        MedicalDirectorySyncResultStatus status = outcome.completed() ? MedicalDirectorySyncResultStatus.COMPLETED
                : outcome.unknown() ? MedicalDirectorySyncResultStatus.RESULT_UNKNOWN
                : MedicalDirectorySyncResultStatus.FAILED;
        return new MedicalDirectorySyncResultVO(
                outcome.type(), status, validation == null ? null : validation.declaredCount(), outcome.returned(),
                validation == null ? 0 : validation.duplicateCount(),
                validation == null ? 0 : validation.invalidCount(),
                validation == null ? 0 : validation.conflictCount(),
                counts == null ? 0 : counts.created(), counts == null ? 0 : counts.updated(),
                counts == null ? 0 : counts.unchanged(), 0,
                outcome.completed() ? mapper.countActiveByType(organizationId, outcome.type()) : null,
                outcome.completed() ? null : outcome.failureSummary());
    }


    /**
     * 合并未完成类型的受控失败原因。
     *
     * @param outcomes 各类型处理结论
     * @return 不超过数据库限制的摘要
     */
    private String summarizeFailures(List<MedicalDirectorySyncResultVO> outcomes) {
        StringJoiner failures = new StringJoiner("；");
        for (MedicalDirectorySyncResultVO outcome : outcomes) {
            if (outcome.status() != MedicalDirectorySyncResultStatus.COMPLETED) {
                failures.add(outcome.directoryType().displayName() + "：" + outcome.failureSummary());
            }
        }
        if (outcomes.size() != MedicalDirectoryType.values().length) failures.add("运行中断，未保存类型结果未知");
        return failures.length() == 0 ? "部分医疗目录未完成同步" : Func.substring(failures.toString(), 0, 500);
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
