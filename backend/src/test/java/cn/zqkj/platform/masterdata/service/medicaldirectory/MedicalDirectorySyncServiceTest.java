package cn.zqkj.platform.masterdata.service.medicaldirectory;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceEntry;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCounts;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataScopeType;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryFetchResult;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryValidationResult;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper;
import cn.zqkj.platform.masterdata.mapper.medicaldirectory.MedicalDirectorySyncMapper;
import cn.zqkj.platform.masterdata.service.medicaldirectory.impl.MedicalDirectorySyncServiceImpl;
import cn.zqkj.platform.masterdata.service.batch.MasterDataBatchService;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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

    /**
     * 验证100-004时间范围没有全量快照证据时，绝不会把未返回历史目录标记无效。
     */
    @Test
    void updatesCompletedTypesWithoutInvalidatingRecordsOutsideTheReturnedTimeRange() {
        Fixture fixture = fixture();
        when(fixture.fetchService().fetchAll(eq(10L), eq(ParameterEnvironment.TEST), any(MedicalDirectoryType.class),
                any(LocalDateTime.class), any(LocalDateTime.class), eq("HIS-ORG-001")))
                .thenAnswer(invocation -> result(invocation.getArgument(2, MedicalDirectoryType.class)));

        fixture.service().fetchValidateAndReconcile(25L, new byte[]{1}, actor());

        verify(fixture.mapper(), never()).markMissingDirectoryInvalid(anyLong(), anyLong(), any());
        verify(fixture.mapper(), times(4)).saveDirectoryResult(eq(25L), any());
        verify(fixture.mapper()).finishCompleted(eq(25L), eq("COMPLETED"), eq(4L), eq(4L),
                eq(0L), eq(0L), eq(0L), eq(4L), eq(0L), eq(0L), eq(0L), eq(4L),
                eq(null), eq(null), eq("admin"));
        verify(fixture.auditService()).recordSuccess(any());
    }

    /**
     * 创建可执行医疗目录批次所需的服务替身。
     *
     * @return 供测试验证的完整依赖集合
     */
    private Fixture fixture() {
        MedicalDirectorySyncMapper mapper = mock(MedicalDirectorySyncMapper.class);
        MasterDataBatchMapper batchMapper = mock(MasterDataBatchMapper.class);
        MasterDataBatchService batchService = mock(MasterDataBatchService.class);
        MedicalDirectoryFetchService fetchService = mock(MedicalDirectoryFetchService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, java.util.function.Consumer.class).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
        doAnswer(invocation -> invocation.getArgument(0, TransactionCallback.class).doInTransaction(null))
                .when(transactions).execute(any());
        when(batchMapper.findById(25L)).thenReturn(snapshot());
        when(batchMapper.findEnabledOrganizationId("ORG001")).thenReturn(10L);
        when(mapper.beginFetch(25L, new byte[]{1}, "admin")).thenReturn(1);
        when(mapper.classifyDirectory(anyLong(), any())).thenReturn(0);
        when(mapper.insertDirectoryDirect(anyLong(), anyLong(), any())).thenReturn(1);
        when(mapper.countActive(10L)).thenReturn(4L);
        when(mapper.countActiveByType(eq(10L), any(MedicalDirectoryType.class))).thenReturn(1L);
        when(mapper.finishCompleted(eq(25L), any(), any(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
                anyLong(), anyLong(), anyLong(), anyLong(), any(), any(), eq("admin"))).thenReturn(1);
        when(batchService.get(25L, actor())).thenReturn(summary());
        ManagementAuditService auditService = mock(ManagementAuditService.class);
        return new Fixture(new MedicalDirectorySyncServiceImpl(mapper, batchMapper, batchService, fetchService,
                auditService, transactions), mapper, fetchService, auditService);
    }

    /**
     * 为一个医疗目录类型创建通过数量核对和字段校验的最小来源结果。
     *
     * @param type 当前医疗目录类型
     * @return 可直接更新当前数据的完整来源结果
     */
    private MedicalDirectoryFetchResult result(MedicalDirectoryType type) {
        MedicalDirectorySourceEntry entry = new MedicalDirectorySourceEntry(type.code(), type.displayName(), null, "目录类别",
                null, null, null, null, null, "2026-09-21 00:00:00", null, null, null, null, null, null,
                null, null, "1");
        MedicalDirectorySourceRecord record = new MedicalDirectorySourceRecord(type, entry);
        return new MedicalDirectoryFetchResult(type,
                new MedicalDirectoryValidationResult(1, 1, 0, 0, 0, List.of(record), null));
    }

    /**
     * 创建有权访问测试机构的当前用户。
     *
     * @return 当前操作人
     */
    private AccessActor actor() {
        return new AccessActor(1L, "admin", Set.of("ORG001"));
    }

    /**
     * 创建包含100-004/100-005同一来源范围的批次快照。
     *
     * @return 可执行医疗目录批次
     */
    private MasterDataBatchSnapshot snapshot() {
        return new MasterDataBatchSnapshot(25L, "BD-TEST", MasterDataScopeType.ORGANIZATION, "ORG001", "测试机构",
                ParameterEnvironment.TEST, MasterDataCategory.MEDICAL_DIRECTORY, "100-004", "100-005", null,
                "HIS-ORG-001", LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 21, 0, 0),
                MasterDataBatchStatus.CREATED, null, 0, 0, 0, 0, 0, 0, 0, 0, null,
                null, null, null, null, null, new byte[]{1});
    }

    /**
     * 创建执行后服务回读使用的批次摘要。
     *
     * @return 已完成医疗目录批次摘要
     */
    private MasterDataBatchSummaryVO summary() {
        return new MasterDataBatchSummaryVO(25L, "BD-TEST", MasterDataScopeType.ORGANIZATION, "ORG001", "测试机构",
                ParameterEnvironment.TEST, MasterDataCategory.MEDICAL_DIRECTORY, "100-004", "100-005", null,
                "HIS-ORG-001", null, null, MasterDataBatchStatus.COMPLETED,
                new MasterDataBatchCounts(4L, 4, 0, 0, 0, 4, 0, 0, 0, 4L),
                null, null, null, null, null, "AQ==");
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
                           MedicalDirectoryFetchService fetchService, ManagementAuditService auditService) {
    }
}
