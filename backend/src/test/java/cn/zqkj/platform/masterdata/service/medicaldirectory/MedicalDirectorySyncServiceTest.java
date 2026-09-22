package cn.zqkj.platform.masterdata.service.medicaldirectory;

import cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectorySyncResultVO;
import java.util.ArrayList;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCounts;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataScopeType;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataSyncMode;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceEntry;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryValidationResult;
import cn.zqkj.platform.masterdata.mapper.medicaldirectory.MedicalDirectorySyncMapper;
import cn.zqkj.platform.masterdata.service.medicaldirectory.impl.MedicalDirectorySyncServiceImpl;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证100-004/100-005按类型完成数量核对和当前目录直接更新。
 */
class MedicalDirectorySyncServiceTest {

    /** 四类均明确失败时不伪装部分成功，不设置有效数或修改目录。 */
    @Test
    void marksAllRejectedTypesAsFailed() {
        Fixture fixture = fixture();
        when(fixture.fetchService().fetchAll(any(), any()))
                .thenThrow(new cn.zqkj.platform.his.exception.PhisBusinessException("100-005失败（结果码0）；交易号TEST"));
        fixture.service().synchronize(snapshot(), 1L, "admin");
        verify(fixture.mapper()).finishCompleted(eq(25L), eq("FAILED"),
                org.mockito.ArgumentMatchers.argThat(counts -> counts.active() == null && counts.created() == 0),
                eq("HIS_ALL_TYPES_FAILED"), any(), eq("admin"));
        verify(fixture.mapper(), never()).insertDirectoryDirect(anyLong(), anyLong(), any());
        verify(fixture.mapper(), times(4)).saveDirectoryResult(eq(25L),
                org.mockito.ArgumentMatchers.argThat(result -> result.failureSummary().contains("交易号TEST")));
    }

    /** 数据库更新零行时不能虚增更新数或报告同步成功。 */
    @Test
    void rejectsZeroRowUpdates() {
        Fixture fixture = fixture();
        when(fixture.fetchService().fetchAll(any(), any())).thenAnswer(call -> result(call.getArgument(1)));
        when(fixture.mapper().classifyDirectory(anyLong(), any())).thenReturn(1);
        when(fixture.mapper().updateDirectoryDirect(anyLong(), anyLong(), any())).thenReturn(0);
        fixture.service().synchronize(snapshot(), 1L, "admin");
        verify(fixture.mapper()).finishCompleted(eq(25L), eq("FAILED"),
                org.mockito.ArgumentMatchers.argThat(counts -> counts.updated() == 0),
                eq("HIS_ALL_TYPES_FAILED"), any(), eq("admin"));
    }

    /** 只有确实提交过成功类型时才使用部分失败状态。 */
    @Test
    void preservesCompletedTypeWhenAnotherTypeFails() {
        Fixture fixture = fixture();
        when(fixture.fetchService().fetchAll(any(), any())).thenThrow(
                new cn.zqkj.platform.his.exception.PhisBusinessException("明确失败"));
        org.mockito.Mockito.doReturn(result(MedicalDirectoryType.CONSUMABLE))
                .when(fixture.fetchService()).fetchAll(any(), eq(MedicalDirectoryType.CONSUMABLE));
        fixture.service().synchronize(snapshot(), 1L, "admin");
        verify(fixture.mapper()).finishCompleted(eq(25L), eq("COMPLETED_WITH_ERRORS"),
                org.mockito.ArgumentMatchers.argThat(counts -> counts.created() == 1),
                eq("HIS_PARTIAL_FAILURE"), any(), eq("admin"));
    }

    /** 医疗目录旧执行版本失效后不能写入目录或分项结果。 */
    @Test
    void fencesStaleMedicalDirectoryWorker() {
        Fixture fixture = fixture();
        when(fixture.batchMapper().lockExecution(anyLong(), any())).thenReturn(false);
        when(fixture.fetchService().fetchAll(any(), any()))
                .thenReturn(result(MedicalDirectoryType.WESTERN_MEDICINE));
        org.junit.jupiter.api.Assertions.assertThrows(cn.zqkj.platform.common.exception.ResourceConflictException.class,
                () -> fixture.service().synchronize(snapshot(), 1L, "admin"));
        org.mockito.Mockito.verifyNoInteractions(fixture.mapper(), fixture.auditService());
    }

    /** 未取得完整分项的医疗批次不能推定来源总数或整批成功。 */
    @Test
    void recoversMissingMedicalResultsAsUnknown() {
        Fixture fixture = fixture();
        fixture.service().completeRecordedResults(snapshot(), 1L, "admin");
        verify(fixture.mapper()).finishCompleted(eq(25L), eq("COMPLETED_WITH_UNKNOWN"),
                org.mockito.ArgumentMatchers.argThat(counts -> counts.declared() == null && counts.returned() == 0),
                eq("HIS_PARTIAL_RESULT_UNKNOWN"), any(), eq("admin"));
        org.mockito.Mockito.verifyNoInteractions(fixture.fetchService());
        verify(fixture.mapper(), never()).saveDirectoryResult(anyLong(), any());
    }

    /** 增量时间范围返回零条时保留历史目录，只保存本轮零变更事实。 */
    @Test
    void keepsExistingDirectoriesWhenIncrementalRangeIsEmpty() {
        Fixture fixture = fixture();
        when(fixture.fetchService().fetchAll(any(), any(MedicalDirectoryType.class)))
                .thenReturn(new MedicalDirectoryValidationResult(0, 0, 0, 0, 0, List.of(), null));

        fixture.service().synchronize(snapshot(), null, "admin");

        verify(fixture.mapper(), never()).classifyDirectory(anyLong(), any());
        verify(fixture.mapper(), never()).insertDirectoryDirect(anyLong(), anyLong(), any());
        verify(fixture.mapper(), never()).updateDirectoryDirect(anyLong(), anyLong(), any());
        verify(fixture.mapper()).finishCompleted(eq(25L), eq("COMPLETED"), eq(new MasterDataBatchCounts(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 4L)), eq(null), eq(null), eq("admin"));
        verify(fixture.auditService()).append(org.mockito.ArgumentMatchers.argThat(
                command -> command.actorUserId() == null && command.resultCode().equals("SUCCESS")));
    }

    /**
     * 验证100-004时间范围没有全量快照证据时，绝不会把未返回历史目录标记无效。
     */
    @Test
    void updatesCompletedTypesWithoutInvalidatingRecordsOutsideTheReturnedTimeRange() {
        Fixture fixture = fixture();
        when(fixture.fetchService().fetchAll(any(), any(MedicalDirectoryType.class)))
                .thenAnswer(invocation -> result(invocation.getArgument(1, MedicalDirectoryType.class)));

        fixture.service().synchronize(snapshot(), 1L, "admin");

        verify(fixture.mapper(), times(4)).insertDirectoryDirect(eq(25L), eq(10L), any());
        verify(fixture.mapper(), never()).updateDirectoryDirect(anyLong(), anyLong(), any());
        verify(fixture.mapper(), times(4)).saveDirectoryResult(eq(25L), any());
        verify(fixture.mapper()).finishCompleted(eq(25L), eq("COMPLETED"), eq(new MasterDataBatchCounts(4L, 4L, 0L, 0L, 0L, 4L, 0L, 0L, 0L, 4L)), eq(null), eq(null), eq("admin"));
        verify(fixture.auditService()).append(any());
    }

    /**
     * 创建可执行医疗目录批次所需的服务替身。
     *
     * @return 供测试验证的完整依赖集合
     */
    private Fixture fixture() {
        MedicalDirectorySyncMapper mapper = mock(MedicalDirectorySyncMapper.class);
        MasterDataBatchMapper batchMapper = mock(MasterDataBatchMapper.class);
        when(batchMapper.lockExecution(anyLong(), any())).thenReturn(true);
        List<MedicalDirectorySyncResultVO> saved = new ArrayList<>();
        doAnswer(invocation -> { saved.add(invocation.getArgument(1)); return null; })
                .when(mapper).saveDirectoryResult(anyLong(), any());
        when(mapper.findResults(25L)).thenAnswer(invocation -> List.copyOf(saved));
        MedicalDirectoryFetchService fetchService = mock(MedicalDirectoryFetchService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Consumer.class).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
        doAnswer(invocation -> invocation.getArgument(0, TransactionCallback.class).doInTransaction(null))
                .when(transactions).execute(any());
        when(mapper.classifyDirectory(anyLong(), any())).thenReturn(0);
        when(mapper.insertDirectoryDirect(anyLong(), anyLong(), any())).thenReturn(1);
        when(mapper.countActive(10L)).thenReturn(4L);
        when(mapper.countActiveByType(eq(10L), any(MedicalDirectoryType.class))).thenReturn(1L);
        when(mapper.finishCompleted(eq(25L), any(), any(), any(), any(), eq("admin"))).thenReturn(1);
        ManagementAuditService auditService = mock(ManagementAuditService.class);
        return new Fixture(new MedicalDirectorySyncServiceImpl(mapper, fetchService,
                auditService, transactions, batchMapper), mapper, fetchService, auditService, batchMapper);
    }

    /**
     * 为一个医疗目录类型创建通过数量核对和字段校验的最小来源结果。
     *
     * @param type 当前医疗目录类型
     * @return 可直接更新当前数据的完整来源结果
     */
    private MedicalDirectoryValidationResult result(MedicalDirectoryType type) {
        MedicalDirectorySourceEntry entry = new MedicalDirectorySourceEntry(type.code(), type.displayName(), null, "目录类别",
                null, null, null, null, null, "2026-09-21 00:00:00", null, null, null, null, null, null,
                null, null, "1");
        MedicalDirectorySourceRecord record = new MedicalDirectorySourceRecord(type, entry);
        return new MedicalDirectoryValidationResult(1, 1, 0, 0, 0, List.of(record), null);
    }


    /**
     * 创建包含100-004/100-005同一来源范围的批次快照。
     *
     * @return 可执行医疗目录批次
     */
    private MasterDataBatchSnapshot snapshot() {
        return new MasterDataBatchSnapshot(25L, "BD-TEST", MasterDataScopeType.ORGANIZATION, 10L, "ORG001", "测试机构",
                ParameterEnvironment.TEST, MasterDataCategory.MEDICAL_DIRECTORY, MasterDataSyncMode.TIME_RANGE,
                "100-004", "100-005", null, "HIS-ORG-001", null, null, null,
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 21, 0, 0),
                MasterDataBatchStatus.FETCHING, null, 0, 0, 0, 0, 0, 0, 0, 0, null,
                null, null, null, null, null, new byte[]{1});
    }


    /**
     * 保存医疗目录同步测试所需服务和替身。
     *
     * @param service 被测服务
     * @param mapper 当前目录持久化边界
     * @param fetchService 100-004/100-005完整取得边界
     * @param auditService 管理审计边界
     */
    private record Fixture(MedicalDirectorySyncService service, MedicalDirectorySyncMapper mapper,
                           MedicalDirectoryFetchService fetchService, ManagementAuditService auditService,
                           MasterDataBatchMapper batchMapper) {
    }
}
