package cn.zqkj.platform.masterdata.service.batch;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.exception.ResourceNotFoundException;
import cn.zqkj.platform.masterdata.domain.batch.dto.StartMasterDataBatchRequest;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataScopeType;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataSyncMode;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper;
import cn.zqkj.platform.masterdata.mapper.hospitaldirectory.HospitalDirectorySyncMapper;
import cn.zqkj.platform.masterdata.mapper.medicaldirectory.MedicalDirectorySyncMapper;
import cn.zqkj.platform.masterdata.service.batch.impl.MasterDataBatchServiceImpl;
import cn.zqkj.platform.masterdata.service.hospitaldirectory.HospitalDirectorySyncService;
import cn.zqkj.platform.masterdata.service.medicaldirectory.MedicalDirectorySyncService;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointAuthentication;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointRuntimeConfiguration;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointScope;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointVerificationStatus;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.configuration.service.ExternalEndpointResolutionService;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证同步批次创建时的业务范围和来源条件。
 */
class MasterDataBatchServiceTest {

    /** 全量批次不接受浏览器自选起止时间。 */
    @Test
    void rejectsFullBatchWithCallerSuppliedRange() {
        var mapper = mock(MasterDataBatchMapper.class);
        var service = new MasterDataBatchServiceImpl(mapper,
                mock(HospitalDirectorySyncMapper.class), mock(MedicalDirectorySyncMapper.class),
                mock(ManagementAuditService.class), availableEndpointService(),
                mock(HospitalDirectorySyncService.class), mock(MedicalDirectorySyncService.class),
                mock(TransactionTemplate.class));
        when(mapper.findEnabledOrganizationId("ORG001")).thenReturn(10L);

        assertThrows(InvalidRequestException.class, () -> service.start(new StartMasterDataBatchRequest(
                "FULL-REQUEST-20260922", "ORG001", ParameterEnvironment.PRODUCTION,
                MasterDataCategory.MEDICAL_DIRECTORY, MasterDataSyncMode.FULL,
                java.time.OffsetDateTime.parse("2026-09-01T09:00:00+08:00"), null), actor()));

        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never())
                .create(any(), any(), any(), anyLong(), any(), any());
    }

    /** 全量入口在服务端冻结创建时刻及向前20年的UTC窗口。 */
    @Test
    void freezesDefaultFullRangeAtCreation() {
        var mapper = mock(MasterDataBatchMapper.class);
        var service = new MasterDataBatchServiceImpl(mapper,
                mock(HospitalDirectorySyncMapper.class), mock(MedicalDirectorySyncMapper.class),
                mock(ManagementAuditService.class), availableEndpointService(),
                mock(HospitalDirectorySyncService.class), mock(MedicalDirectorySyncService.class),
                mock(TransactionTemplate.class));
        when(mapper.findEnabledOrganizationId("ORG001")).thenReturn(10L);
        when(mapper.create(any(), any(), any(), anyLong(), any(), any())).thenReturn(25L);
        when(mapper.findById(25L)).thenReturn(snapshot());
        var beforeCreation = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1);

        service.start(new StartMasterDataBatchRequest("FULL-REQUEST-20260922", "ORG001",
                ParameterEnvironment.PRODUCTION, MasterDataCategory.MEDICAL_DIRECTORY,
                MasterDataSyncMode.FULL, null, null), actor());
        var afterCreation = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(1);

        org.mockito.Mockito.verify(mapper).create(any(), any(), org.mockito.ArgumentMatchers.argThat(creation ->
                        creation.mode() == MasterDataSyncMode.FULL
                                && creation.rangeStart().equals(creation.rangeEnd().minusYears(20))
                                && creation.rangeEnd().isAfter(beforeCreation)
                                && creation.rangeEnd().isBefore(afterCreation)
                                && creation.fullRuleEvidence().contains("不证明HIS更早或无时间记录已覆盖")
                                && creation.sourceEndpointId() == 9L),
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq("ORG001"),
                org.mockito.ArgumentMatchers.eq("admin"));
    }

    /** 页面创建医疗批次时必须固化已验证的平台交易机构编码，不能换成100-008返回ID。 */
    @Test
    void createsMedicalBatchWithPlatformOrganizationCode() {
        var mapper = mock(MasterDataBatchMapper.class);
        var service = new MasterDataBatchServiceImpl(mapper,
                mock(HospitalDirectorySyncMapper.class), mock(MedicalDirectorySyncMapper.class),
                mock(ManagementAuditService.class), availableEndpointService(),
                mock(HospitalDirectorySyncService.class), mock(MedicalDirectorySyncService.class), mock(TransactionTemplate.class));
        when(mapper.findEnabledOrganizationId("ORG001")).thenReturn(10L);
        when(mapper.create(any(), any(), any(), anyLong(), any(), any())).thenReturn(25L);
        when(mapper.findById(25L)).thenReturn(snapshot());
        var request = new StartMasterDataBatchRequest("TEST-REQUEST-20260922", "ORG001",
                ParameterEnvironment.PRODUCTION, MasterDataCategory.MEDICAL_DIRECTORY, MasterDataSyncMode.TIME_RANGE,
                java.time.OffsetDateTime.parse("2026-09-01T09:00:00+08:00"),
                java.time.OffsetDateTime.parse("2026-09-22T09:00:00+08:00"));
        service.start(request, actor());
        verify(mapper).create(any(), org.mockito.ArgumentMatchers.eq("PRIMARY_HIS"),
                org.mockito.ArgumentMatchers.argThat(creation -> creation.mode() == MasterDataSyncMode.TIME_RANGE
                        && creation.organizationCode().equals(request.organizationCode())), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("ORG001"), org.mockito.ArgumentMatchers.eq("admin"));
    }

    /** 不存在、越权或尚未执行的批次不能进入恢复收尾。 */
    @Test
    void rejectsInvisibleOrUnstartedBatchRecovery() {
        var mapper = mock(MasterDataBatchMapper.class);
        var hospital = mock(HospitalDirectorySyncService.class);
        var medical = mock(MedicalDirectorySyncService.class);
        var service = new MasterDataBatchServiceImpl(mapper,
                mock(HospitalDirectorySyncMapper.class), mock(MedicalDirectorySyncMapper.class),
                mock(ManagementAuditService.class), availableEndpointService(), hospital, medical,
                mock(TransactionTemplate.class));
        assertThrows(ResourceNotFoundException.class, () -> service.recover(25L, new byte[8], actor()));
        when(mapper.findVisibleById(25L, List.of("ORG001"))).thenReturn(snapshot());
        assertThrows(ResourceConflictException.class, () -> service.recover(25L, new byte[8], actor()));
        verifyNoInteractions(hospital, medical);
    }

    /** 只有范围内且抢占成功的批次才能调用HIS，抢占事务必须先结束。 */
    @Test
    void claimsBatchBeforeStartingExternalSynchronization() {
        MasterDataBatchMapper mapper = mock(MasterDataBatchMapper.class);
        HospitalDirectorySyncService hospital = mock(HospitalDirectorySyncService.class);
        MedicalDirectorySyncService medical = mock(MedicalDirectorySyncService.class);
        var manager = mock(PlatformTransactionManager.class);
        var transaction = mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(transaction);
        MasterDataBatchService service = new MasterDataBatchServiceImpl(mapper,
                mock(HospitalDirectorySyncMapper.class), mock(MedicalDirectorySyncMapper.class),
                mock(ManagementAuditService.class), availableEndpointService(), hospital, medical,
                new TransactionTemplate(manager));
        MasterDataBatchSnapshot batch = snapshot();
        when(mapper.findVisibleById(25L, List.of("ORG001"))).thenReturn(batch);
        when(mapper.findEnabledOrganizationId("ORG001")).thenReturn(10L);
        when(mapper.beginFetch(25L, new byte[8], "admin")).thenReturn(1);
        when(mapper.findById(25L)).thenReturn(batch);

        service.run(25L, new byte[8], actor());

        var order = Mockito.inOrder(mapper, manager, hospital);
        order.verify(mapper).beginFetch(25L, new byte[8], "admin");
        order.verify(manager).commit(transaction);
        order.verify(hospital).synchronize(batch, 1L, "admin");
        verifyNoInteractions(medical);
    }

    /** 范围外或并发抢占失败时，不得继续触发同步。 */
    @Test
    void rejectsInvisibleOrAlreadyClaimedBatchBeforeCallingHis() {
        MasterDataBatchMapper mapper = mock(MasterDataBatchMapper.class);
        HospitalDirectorySyncService hospital = mock(HospitalDirectorySyncService.class);
        MedicalDirectorySyncService medical = mock(MedicalDirectorySyncService.class);
        var manager = mock(PlatformTransactionManager.class);
        MasterDataBatchService service = new MasterDataBatchServiceImpl(mapper,
                mock(HospitalDirectorySyncMapper.class), mock(MedicalDirectorySyncMapper.class),
                mock(ManagementAuditService.class), availableEndpointService(), hospital, medical,
                new TransactionTemplate(manager));

        assertThrows(ResourceNotFoundException.class, () -> service.run(25L, new byte[8], actor()));
        verifyNoInteractions(manager, hospital, medical);

        when(mapper.findVisibleById(25L, List.of("ORG001"))).thenReturn(snapshot());
        when(mapper.findEnabledOrganizationId("ORG001")).thenReturn(10L);
        assertThrows(ResourceConflictException.class,
                () -> service.run(25L, new byte[8], actor()));
        verifyNoInteractions(hospital, medical);
    }

    /** 验证发起选项由真实HIS范围和后端已实现业务共同生成。 */
    @Test
    void returnsConfiguredSourcesAndImplementedBusinesses() {
        MasterDataBatchMapper mapper = mock(MasterDataBatchMapper.class);
        ExternalEndpointResolutionService endpointService = mock(ExternalEndpointResolutionService.class);
        when(endpointService.findAvailableScopes("PRIMARY_HIS", List.of("ORG001")))
                .thenReturn(List.of(new ExternalEndpointScope(
                        10L, "ORG001", "测试机构", ParameterEnvironment.PRODUCTION)));
        MasterDataBatchService service = new MasterDataBatchServiceImpl(mapper, mock(HospitalDirectorySyncMapper.class), mock(MedicalDirectorySyncMapper.class),
                mock(ManagementAuditService.class), endpointService,
                mock(HospitalDirectorySyncService.class), mock(MedicalDirectorySyncService.class),
                mock(TransactionTemplate.class));

        var result = service.findSyncOptions(List.of("ORG001"));

        assertEquals("ORG001", result.sources().get(0).organizationCode());
        assertEquals(List.of(ParameterEnvironment.PRODUCTION), result.sources().get(0).environments());
        assertEquals(MasterDataCategory.HOSPITAL_DIRECTORY, result.businesses().get(0).category());
        assertEquals("100-003", result.businesses().get(0).tradeCode());
    }

    /** 验证医院综合目录同步不让调用方选择100-003的某一个目录类型。 */
    @Test
    void createsHospitalDirectoryBatchWithoutSourceType() {
        MasterDataBatchMapper mapper = mock(MasterDataBatchMapper.class);
        ExternalEndpointResolutionService endpointService = availableEndpointService();
        MasterDataBatchService service = new MasterDataBatchServiceImpl(mapper, mock(HospitalDirectorySyncMapper.class), mock(MedicalDirectorySyncMapper.class),
                mock(ManagementAuditService.class), endpointService,
                mock(HospitalDirectorySyncService.class), mock(MedicalDirectorySyncService.class),
                mock(TransactionTemplate.class));
        when(mapper.findEnabledOrganizationId("ORG001")).thenReturn(10L);
        when(mapper.create(any(), any(), any(), anyLong(), any(), any())).thenReturn(25L);
        when(mapper.findById(25L)).thenReturn(snapshot());

        service.start(new StartMasterDataBatchRequest(
                "8BCDCA4E11A44A9D888D7E70", "ORG001", ParameterEnvironment.PRODUCTION,
                MasterDataCategory.HOSPITAL_DIRECTORY, MasterDataSyncMode.NOT_APPLICABLE, null, null
        ), actor());

        verify(mapper).findEnabledOrganizationId("ORG001");
    }

    /** 验证接口配置没有100-008来源机构结果时不能创建业务批次。 */
    @Test
    void rejectsHospitalDirectoryBatchWithoutVerifiedSourceOrganization() {
        MasterDataBatchMapper mapper = mock(MasterDataBatchMapper.class);
        ExternalEndpointResolutionService endpointService = mock(ExternalEndpointResolutionService.class);
        when(endpointService.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.PRODUCTION, 10L))
                .thenReturn(Optional.of(new ExternalEndpointRuntimeConfiguration(
                        new ExternalEndpoint(9L, 1L, ParameterEnvironment.PRODUCTION, 10L, "ORG001",
                                "http://his.example.invalid/WebService.asmx", 3000, 15000, "managed://database",
                                true, true, null, null, new byte[8], "测试机构", null, null,
                                ExternalEndpointVerificationStatus.VERIFIED, null, null),
                        new ExternalEndpointAuthentication("V01", null, null, "AUTH"))));
        MasterDataBatchService service = new MasterDataBatchServiceImpl(mapper, mock(HospitalDirectorySyncMapper.class), mock(MedicalDirectorySyncMapper.class),
                mock(ManagementAuditService.class), endpointService,
                mock(HospitalDirectorySyncService.class), mock(MedicalDirectorySyncService.class),
                mock(TransactionTemplate.class));
        when(mapper.findEnabledOrganizationId("ORG001")).thenReturn(10L);

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> service.start(new StartMasterDataBatchRequest(
                        "8BCDCA4E11A44A9D888D7E70", "ORG001", ParameterEnvironment.PRODUCTION,
                        MasterDataCategory.HOSPITAL_DIRECTORY, MasterDataSyncMode.NOT_APPLICABLE, null, null
                ), actor()));

        assertEquals("该机构的基层HIS配置未保存100-008来源机构结果，不能发起同步", exception.getMessage());
    }

    /** 验证尚未执行的批次可受控取消且不会删除历史记录。 */
    @Test
    void cancelsCreatedBatchWithOptimisticVersion() {
        MasterDataBatchMapper mapper = mock(MasterDataBatchMapper.class);
        MasterDataBatchService service = new MasterDataBatchServiceImpl(mapper, mock(HospitalDirectorySyncMapper.class), mock(MedicalDirectorySyncMapper.class),
                mock(ManagementAuditService.class), availableEndpointService(),
                mock(HospitalDirectorySyncService.class), mock(MedicalDirectorySyncService.class),
                mock(TransactionTemplate.class));
        MasterDataBatchSnapshot created = snapshot();
        MasterDataBatchSnapshot cancelled = new MasterDataBatchSnapshot(
                25L, "BD-TEST", MasterDataScopeType.ORGANIZATION, 10L, "ORG001", "测试机构",
                ParameterEnvironment.PRODUCTION,
                MasterDataCategory.HOSPITAL_DIRECTORY, MasterDataSyncMode.NOT_APPLICABLE,
                "100-003", null, null, "HIS-ORG-001", null, null, null, null, null,
                MasterDataBatchStatus.FAILED, null, 0, 0, 0, 0, 0, 0, 0, 0, null,
                null, null, null, "CANCELLED_BY_USER", "本批次已由业务人员取消，未调用来源HIS。", new byte[]{2}
        );
        when(mapper.findVisibleById(25L, List.of("ORG001"))).thenReturn(created);
        when(mapper.findById(25L)).thenReturn(cancelled);
        when(mapper.cancel(25L, new byte[8], "admin", List.of("ORG001"))).thenReturn(1);

        MasterDataBatchSummaryVO result = service.cancel(25L, new byte[8], actor());

        assertEquals("CANCELLED_BY_USER", result.failureCode());
        verify(mapper).cancel(25L, new byte[8], "admin", List.of("ORG001"));
    }

    /** 验证批次服务只负责可见性和类别边界，100-003结果由医院目录持久化边界读取。 */
    @Test
    void readsHospitalDirectoryResultsThroughHospitalDirectoryMapper() {
        MasterDataBatchMapper batchMapper = mock(MasterDataBatchMapper.class);
        HospitalDirectorySyncMapper directoryMapper = mock(HospitalDirectorySyncMapper.class);
        MasterDataBatchService service = new MasterDataBatchServiceImpl(batchMapper, directoryMapper, mock(MedicalDirectorySyncMapper.class),
                mock(ManagementAuditService.class), availableEndpointService(),
                mock(HospitalDirectorySyncService.class), mock(MedicalDirectorySyncService.class),
                mock(TransactionTemplate.class));
        when(batchMapper.findVisibleById(25L, List.of("ORG001"))).thenReturn(snapshot());
        when(directoryMapper.findResults(25L)).thenReturn(List.of());

        service.findHospitalDirectoryResults(25L, List.of("ORG001"));

        verify(directoryMapper).findResults(25L);
    }

    /** 验证100-004/100-005结果由医疗目录持久化边界读取，而非混入批次Mapper。 */
    @Test
    void readsMedicalDirectoryResultsThroughMedicalDirectoryMapper() {
        MasterDataBatchMapper batchMapper = mock(MasterDataBatchMapper.class);
        MedicalDirectorySyncMapper directoryMapper = mock(MedicalDirectorySyncMapper.class);
        MasterDataBatchService service = new MasterDataBatchServiceImpl(batchMapper, mock(HospitalDirectorySyncMapper.class), directoryMapper,
                mock(ManagementAuditService.class), availableEndpointService(),
                mock(HospitalDirectorySyncService.class), mock(MedicalDirectorySyncService.class),
                mock(TransactionTemplate.class));
        MasterDataBatchSnapshot medicalSnapshot = new MasterDataBatchSnapshot(
                25L, "BD-TEST", MasterDataScopeType.ORGANIZATION, 10L, "ORG001", "测试机构",
                ParameterEnvironment.PRODUCTION,
                MasterDataCategory.MEDICAL_DIRECTORY, MasterDataSyncMode.TIME_RANGE,
                "100-004", "100-005", null, "HIS-ORG-001", null, null, null, null, null,
                MasterDataBatchStatus.COMPLETED, null, 0, 0, 0, 0, 0, 0, 0, 0, 0L,
                null, null, null, null, null, new byte[8]);
        when(batchMapper.findVisibleById(25L, List.of("ORG001"))).thenReturn(medicalSnapshot);
        when(directoryMapper.findResults(25L)).thenReturn(List.of());

        service.findMedicalDirectoryResults(25L, List.of("ORG001"));

        verify(directoryMapper).findResults(25L);
    }

    /** 范围外批次不得读取摘要或继续读取其分项事实。 */
    @Test
    void hidesBatchOutsideCallerOrganizationScope() {
        MasterDataBatchMapper batchMapper = mock(MasterDataBatchMapper.class);
        HospitalDirectorySyncMapper directoryMapper = mock(HospitalDirectorySyncMapper.class);
        MasterDataBatchService service = new MasterDataBatchServiceImpl(batchMapper, directoryMapper, mock(MedicalDirectorySyncMapper.class),
                mock(ManagementAuditService.class), availableEndpointService(),
                mock(HospitalDirectorySyncService.class), mock(MedicalDirectorySyncService.class),
                mock(TransactionTemplate.class));

        assertThrows(ResourceNotFoundException.class, () -> service.get(25L, List.of()));
        assertThrows(ResourceNotFoundException.class,
                () -> service.findHospitalDirectoryResults(25L, List.of()));

        verify(batchMapper, Mockito.times(2)).findVisibleById(25L, List.of());
        verifyNoInteractions(directoryMapper);
    }

    /**
     * 创建具有测试机构范围的服务层操作人。
     *
     * @return 具有测试机构范围的操作人
     */
    private AccessActor actor() {
        return new AccessActor(1L, "admin", Set.of("ORG001"));
    }

    /**
     * 创建对测试机构返回可用端点的解析服务替身。
     *
     * @return 对测试机构生产环境返回可用配置的解析服务
     */
    private ExternalEndpointResolutionService availableEndpointService() {
        ExternalEndpointResolutionService service = mock(ExternalEndpointResolutionService.class);
        ExternalEndpoint endpoint = new ExternalEndpoint(
                9L, 1L, ParameterEnvironment.PRODUCTION, 10L, "ORG001",
                "http://his.example.invalid/WebService.asmx", 3000, 15000, "managed://database",
                true, true, null, null, new byte[8], "测试机构", "HIS-ORG-001", "测试机构",
                ExternalEndpointVerificationStatus.VERIFIED, null, null);
        when(service.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.PRODUCTION, 10L))
                .thenReturn(Optional.of(new ExternalEndpointRuntimeConfiguration(
                        endpoint, new ExternalEndpointAuthentication("V01", null, null, "AUTH"))));
        return service;
    }

    /**
     * 创建处于可执行状态的同步批次快照。
     *
     * @return 新建状态的测试批次快照
     */
    private MasterDataBatchSnapshot snapshot() {
        return new MasterDataBatchSnapshot(
                25L, "BD-TEST", MasterDataScopeType.ORGANIZATION, 10L, "ORG001", "测试机构",
                ParameterEnvironment.PRODUCTION,
                MasterDataCategory.HOSPITAL_DIRECTORY, MasterDataSyncMode.NOT_APPLICABLE,
                "100-003", null, null, "HIS-ORG-001", null, null, null, null, null,
                MasterDataBatchStatus.CREATED, null, 0, 0, 0, 0, 0, 0, 0, 0, null,
                null, null, null, null, null, new byte[8]
        );
    }
}
