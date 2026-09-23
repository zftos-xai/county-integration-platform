package cn.zqkj.platform.masterdata.service.icd10.impl;

import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.his.exception.PhisBusinessException;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisConfigurationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCounts;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10DiagnosisCategory;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10SourceRecord;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10SyncResultStatus;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10ValidationResult;
import cn.zqkj.platform.masterdata.domain.icd10.vo.Icd10SyncResultVO;
import cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper;
import cn.zqkj.platform.masterdata.mapper.icd10.Icd10SyncMapper;
import cn.zqkj.platform.masterdata.service.icd10.Icd10FetchService;
import cn.zqkj.platform.masterdata.service.icd10.Icd10SyncService;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import java.util.List;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 将100-006/100-007公共诊断目录按西医、中医类别独立发布为当前平台数据。 */
@Service
public class Icd10SyncServiceImpl implements Icd10SyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(Icd10SyncServiceImpl.class);
    private static final int EMPTY_CATEGORY_INSERT_CHUNK_SIZE = 200;
    private final Icd10SyncMapper mapper;
    private final MasterDataBatchMapper batchMapper;
    private final Icd10FetchService fetchService;
    private final ManagementAuditService auditService;
    private final TransactionTemplate transactions;

    /**
     * 创建公共ICD10同步服务。
     *
     * @param mapper 公共目录与分项结果持久化边界
     * @param batchMapper 批次执行和冻结端点栅栏
     * @param fetchService HIS完整取数边界
     * @param auditService 管理审计追加服务
     * @param transactions 短事务模板
     */
    public Icd10SyncServiceImpl(Icd10SyncMapper mapper, MasterDataBatchMapper batchMapper,
                                Icd10FetchService fetchService, ManagementAuditService auditService,
                                TransactionTemplate transactions) {
        this.mapper = mapper;
        this.batchMapper = batchMapper;
        this.fetchService = fetchService;
        this.auditService = auditService;
        this.transactions = transactions;
    }

    /** {@inheritDoc} */
    @Override
    public void synchronize(MasterDataBatchSnapshot batch, long endpointOrganizationId,
                            Long actorUserId, String actorLogin) {
        for (Icd10DiagnosisCategory category : Icd10DiagnosisCategory.values()) {
            Outcome outcome = fetchAndSynchronize(batch, endpointOrganizationId, category);
            if (!outcome.completed()) {
                transactions.executeWithoutResult(ignored -> {
                    requireExecution(batch);
                    mapper.saveResult(batch.id(), toResult(outcome));
                });
            }
        }
        completeRecordedResults(batch, actorUserId, actorLogin);
    }

    /** 取得、校验并在短事务内发布一类诊断目录。 */
    private Outcome fetchAndSynchronize(MasterDataBatchSnapshot batch, long endpointOrganizationId,
                                        Icd10DiagnosisCategory category) {
        Icd10ValidationResult validation;
        try {
            validation = fetchService.fetchAll(batch, endpointOrganizationId, category);
        } catch (PhisCommunicationException | PhisProtocolException exception) {
            return Outcome.unknown(category, exception.getMessage());
        } catch (PhisBusinessException | PhisConfigurationException exception) {
            return Outcome.failed(category, exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("ICD10目录取得失败｜批次{}｜类别{}｜{}", batch.id(), category.code(),
                    Func.rootCause(exception).getClass().getSimpleName());
            return Outcome.failed(category, "平台未能完成" + category.displayName() + "目录取得，当前目录未改变");
        }
        if (!validation.valid()) return Outcome.failed(category, validation, validation.failureSummary());
        try {
            return transactions.execute(ignored -> {
                requireExecution(batch);
                if (batchMapper.hasBoundSourceChanged(batch.id())) {
                    throw new ResourceConflictException("目录取得期间HIS端点配置已改变，该类别未更新公共目录");
                }
                MutationCounts counts = synchronizeCategory(batch.id(), validation.acceptedRecords());
                Outcome outcome = Outcome.completed(category, validation, counts);
                mapper.saveResult(batch.id(), toResult(outcome));
                return outcome;
            });
        } catch (ResourceConflictException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error("ICD10目录保存失败｜批次{}｜类别{}｜{}", batch.id(), category.code(),
                    Func.rootCause(exception).getClass().getSimpleName());
            return Outcome.failed(category, validation, "平台保存" + category.displayName() + "目录失败，当前目录未改变");
        }
    }

    /** 在持有执行栅栏的事务内对已校验来源事实执行新增、更新或不变统计。 */
    private MutationCounts synchronizeCategory(long batchId, List<Icd10SourceRecord> records) {
        if (records.isEmpty()) {
            return new MutationCounts(0, 0, 0);
        }
        if (mapper.countActiveByCategory(records.get(0).category()) == 0) {
            return insertEmptyCategory(batchId, records);
        }
        long created = 0;
        long updated = 0;
        long unchanged = 0;
        for (Icd10SourceRecord record : records) {
            int classification = mapper.classifyDiagnosis(record);
            if (classification == 0 && mapper.insertDiagnosisDirect(batchId, record) == 1) {
                created++;
                continue;
            }
            int current = classification == 0 ? mapper.classifyDiagnosis(record) : classification;
            if (mapper.updateDiagnosisDirect(batchId, record) != 1) {
                throw new IllegalStateException("公共ICD10目录更新未命中唯一记录");
            }
            if (current == 1) updated++;
            else unchanged++;
        }
        return new MutationCounts(created, updated, unchanged);
    }

    /**
     * 首次接入某一类别时按SQL Server参数上限分批写入，避免逐条分类造成大量数据库往返。
     *
     * <p>来源在进入此方法前已经完成同类别稳定身份去重；若执行栅栏内出现并发新增，受影响
     * 批次不会伪造计数，而是抛出异常并回滚当前类别事务。</p>
     */
    private MutationCounts insertEmptyCategory(long batchId, List<Icd10SourceRecord> records) {
        long created = 0;
        for (int start = 0; start < records.size(); start += EMPTY_CATEGORY_INSERT_CHUNK_SIZE) {
            int end = Math.min(start + EMPTY_CATEGORY_INSERT_CHUNK_SIZE, records.size());
            List<Icd10SourceRecord> chunk = records.subList(start, end);
            int inserted = mapper.insertDiagnosesDirect(batchId, chunk);
            if (inserted != chunk.size()) {
                throw new IllegalStateException("公共ICD10首次写入期间发现并发来源身份，当前类别未更新");
            }
            created += inserted;
        }
        return new MutationCounts(created, 0, 0);
    }

    /** {@inheritDoc} */
    @Override
    public void completeRecordedResults(MasterDataBatchSnapshot batch, Long actorUserId, String actorLogin) {
        transactions.executeWithoutResult(ignored -> {
            requireExecution(batch);
            List<Icd10SyncResultVO> results = mapper.findResults(batch.id());
            long declared = 0, returned = 0, duplicate = 0, invalid = 0, conflict = 0, created = 0, updated = 0, unchanged = 0;
            boolean declaredKnown = results.size() == Icd10DiagnosisCategory.values().length;
            boolean unknown = results.size() != Icd10DiagnosisCategory.values().length;
            boolean allCompleted = !unknown;
            int completed = 0;
            for (Icd10SyncResultVO result : results) {
                if (result.declaredCount() == null) declaredKnown = false; else declared += result.declaredCount();
                returned += result.returnedCount(); duplicate += result.duplicateCount(); invalid += result.invalidCount();
                conflict += result.conflictCount(); created += result.createdCount(); updated += result.updatedCount();
                unchanged += result.unchangedCount();
                unknown |= result.status() == Icd10SyncResultStatus.RESULT_UNKNOWN;
                allCompleted &= result.status() == Icd10SyncResultStatus.COMPLETED;
                if (result.status() == Icd10SyncResultStatus.COMPLETED) completed++;
            }
            String status = allCompleted ? "COMPLETED" : unknown ? "COMPLETED_WITH_UNKNOWN"
                    : completed == 0 ? "FAILED" : "COMPLETED_WITH_ERRORS";
            String failureCode = allCompleted ? null : unknown ? "HIS_PARTIAL_RESULT_UNKNOWN"
                    : completed == 0 ? "HIS_ALL_TYPES_FAILED" : "HIS_PARTIAL_FAILURE";
            MasterDataBatchCounts counts = new MasterDataBatchCounts(declaredKnown ? declared : null, returned,
                    duplicate, invalid, conflict, created, updated, unchanged, 0,
                    "FAILED".equals(status) ? null : mapper.countActive());
            if (mapper.finishCompleted(batch.id(), status, counts, failureCode, summarizeFailures(results), actorLogin) != 1) {
                throw new ResourceConflictException("同步运行状态已经变化，请重新读取后再操作");
            }
            auditService.append(new ManagementAuditCommand(actorUserId, actorLogin, null, null,
                    "MASTER_DATA_ICD10_SYNCED", "MASTER_DATA_BATCH", batch.batchNo(),
                    allCompleted ? "SUCCESS" : "FAILURE", "公共ICD10批次结束；已保存分项" + results.size()
                            + "类，新增" + created + "条，更新" + updated + "条", null));
        });
    }

    /** 确认仍持有执行版本，阻止旧执行者在恢复或结束后继续写入。 */
    private void requireExecution(MasterDataBatchSnapshot batch) {
        if (!batchMapper.lockExecution(batch.id(), batch.version())) {
            throw new ResourceConflictException("批次执行权已失效，请重新读取批次");
        }
    }

    /** 将运行结论转换为满足表约束的单类别事实。 */
    private Icd10SyncResultVO toResult(Outcome outcome) {
        Icd10ValidationResult validation = outcome.validation();
        MutationCounts counts = outcome.counts();
        Icd10SyncResultStatus status = outcome.completed() ? Icd10SyncResultStatus.COMPLETED
                : outcome.unknown() ? Icd10SyncResultStatus.RESULT_UNKNOWN : Icd10SyncResultStatus.FAILED;
        return new Icd10SyncResultVO(outcome.category(), status, validation == null ? null : validation.declaredCount(),
                outcome.returned(), validation == null ? 0 : validation.duplicateCount(),
                validation == null ? 0 : validation.invalidCount(), validation == null ? 0 : validation.conflictCount(),
                counts == null ? 0 : counts.created(), counts == null ? 0 : counts.updated(),
                counts == null ? 0 : counts.unchanged(), outcome.completed() ? mapper.countActiveByCategory(outcome.category()) : null,
                outcome.completed() ? null : outcome.failureSummary());
    }

    /** 汇总未完成类别的受控失败摘要。 */
    private String summarizeFailures(List<Icd10SyncResultVO> results) {
        StringJoiner failures = new StringJoiner("；");
        for (Icd10SyncResultVO result : results) {
            if (result.status() != Icd10SyncResultStatus.COMPLETED) {
                failures.add(result.diagnosisCategory().displayName() + "：" + result.failureSummary());
            }
        }
        if (results.size() != Icd10DiagnosisCategory.values().length) failures.add("运行中断，未保存类别结果未知");
        return failures.length() == 0 ? null : Func.substring(failures.toString(), 0, 500);
    }

    /** 一个类别的当前目录变更计数。 */
    private record MutationCounts(long created, long updated, long unchanged) { }

    /** 一个类别的外部取得、校验和发布结论。 */
    private record Outcome(Icd10DiagnosisCategory category, boolean completed, boolean unknown, long returned,
                           String failureSummary, Icd10ValidationResult validation, MutationCounts counts) {
        /** 创建完整发布结论。 */
        private static Outcome completed(Icd10DiagnosisCategory category, Icd10ValidationResult validation, MutationCounts counts) {
            return new Outcome(category, true, false, validation.returnedCount(), null, validation, counts);
        }
        /** 创建明确失败结论。 */
        private static Outcome failed(Icd10DiagnosisCategory category, String summary) {
            return new Outcome(category, false, false, 0, summary, null, null);
        }
        /** 创建取得后未通过校验或发布失败结论。 */
        private static Outcome failed(Icd10DiagnosisCategory category, Icd10ValidationResult validation, String summary) {
            return new Outcome(category, false, false, validation.returnedCount(), summary, validation, null);
        }
        /** 创建来源结果未知结论。 */
        private static Outcome unknown(Icd10DiagnosisCategory category, String summary) {
            return new Outcome(category, false, true, 0, summary, null, null);
        }
    }
}
