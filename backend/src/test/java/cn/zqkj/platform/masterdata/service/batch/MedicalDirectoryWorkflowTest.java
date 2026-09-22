package cn.zqkj.platform.masterdata.service.batch;

import cn.zqkj.platform.exchange.service.ExchangeRuntimeRecordService;
import cn.zqkj.platform.his.domain.medicaldirectory.model.MedicalDirectoryEntry;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.masterdata.domain.batch.dto.StartMasterDataBatchRequest;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCreation;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataScopeType;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataSyncMode;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectorySyncResultVO;
import cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper;
import cn.zqkj.platform.masterdata.mapper.hospitaldirectory.HospitalDirectorySyncMapper;
import cn.zqkj.platform.masterdata.mapper.medicaldirectory.MedicalDirectorySyncMapper;
import cn.zqkj.platform.masterdata.service.batch.impl.MasterDataBatchServiceImpl;
import cn.zqkj.platform.masterdata.service.hospitaldirectory.HospitalDirectorySyncService;
import cn.zqkj.platform.masterdata.service.medicaldirectory.impl.MedicalDirectoryFetchServiceImpl;
import cn.zqkj.platform.masterdata.service.medicaldirectory.impl.MedicalDirectorySyncServiceImpl;
import cn.zqkj.platform.masterdata.service.medicaldirectory.impl.MedicalDirectoryValidationServiceImpl;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointRuntimeConfiguration;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.configuration.mapper.ConfigurationMapper;
import cn.zqkj.platform.system.configuration.service.ExternalEndpointResolutionService;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证页面请求经过真实创建、运行、取数和校验服务后仍保留正确机构与时间；数据库和HIS为受控替身。 */
class MedicalDirectoryWorkflowTest {

    /** 不直接构造查询跳过创建流程，回归机构参数在层间被错误替换及UTC被当本地时间发送的问题。 */
    @Test
    void carriesCreatedBatchScopeThroughRunToEveryHisCall() {
        var mapper = mock(MasterDataBatchMapper.class);
        var directoryMapper = mock(MedicalDirectorySyncMapper.class);
        var endpoints = mock(ExternalEndpointResolutionService.class);
        var endpoint = mock(ExternalEndpoint.class);
        when(endpoint.sourceOrganizationId()).thenReturn("DIFFERENT-HIS-ID");
        when(endpoint.id()).thenReturn(9L);
        when(endpoint.version()).thenReturn(new byte[8]);
        when(endpoints.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, 8L))
                .thenReturn(Optional.of(new ExternalEndpointRuntimeConfiguration(endpoint, null)));
        var audit = mock(ManagementAuditService.class);
        var records = mock(ExchangeRuntimeRecordService.class);
        var his = mock(PhisService.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var transactions = new TransactionTemplate(manager);
        var fetch = new MedicalDirectoryFetchServiceImpl(his, new MedicalDirectoryValidationServiceImpl(), records, "Asia/Shanghai");
        var sync = new MedicalDirectorySyncServiceImpl(directoryMapper, fetch, audit, transactions, mapper);
        var service = new MasterDataBatchServiceImpl(mapper, mock(ConfigurationMapper.class), mock(HospitalDirectorySyncMapper.class), directoryMapper,
                audit, endpoints, mock(HospitalDirectorySyncService.class), sync, transactions);
        var persisted = new AtomicReference<MasterDataBatchSnapshot>();
        when(mapper.findEnabledOrganizationId("ORG-008")).thenReturn(8L);
        when(mapper.create(any(), any(), any(), eq(8L), any(), any())).thenAnswer(call -> {
            MasterDataBatchCreation creation = call.getArgument(2);
            persisted.set(new MasterDataBatchSnapshot(25L, call.getArgument(0), MasterDataScopeType.ORGANIZATION,
                    8L, creation.organizationCode(), "测试机构", creation.environment(), creation.category(), creation.mode(),
                    "100-004", "100-005", null, call.getArgument(4), creation.sourceEndpointId(),
                    creation.sourceEndpointVersion(), creation.fullRuleEvidence(), creation.rangeStart(), creation.rangeEnd(),
                    MasterDataBatchStatus.CREATED,
                    null, 0, 0, 0, 0, 0, 0, 0, 0, null, null, null, null, null, null, new byte[8]));
            return 25L;
        });
        when(mapper.findById(25L)).thenAnswer(call -> persisted.get());
        when(mapper.findVisibleById(25L, List.of("ORG-008"))).thenAnswer(call -> persisted.get());
        when(mapper.beginFetch(eq(25L), any(), eq("tester"))).thenReturn(1);
        when(mapper.lockExecution(eq(25L), any())).thenReturn(true);
        var results = new ArrayList<MedicalDirectorySyncResultVO>();
        doAnswer(call -> { results.add(call.getArgument(1)); return null; })
                .when(directoryMapper).saveDirectoryResult(eq(25L), any());
        when(directoryMapper.findResults(25L)).thenAnswer(call -> List.copyOf(results));
        when(directoryMapper.insertDirectoryDirect(eq(25L), eq(8L), any())).thenReturn(1);
        when(directoryMapper.finishCompleted(eq(25L), any(), any(), any(), any(), eq("tester"))).thenReturn(1);
        when(his.countMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), any())).thenReturn(PhisResponse.success("1", 1L));
        when(his.queryMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), any())).thenReturn(PhisResponse.success("1",
                List.of(new MedicalDirectoryEntry("C1", "合成目录", null, "类别", null, null, null, null, null,
                        "2026-09-01 09:00:00", null, null, null, null, null, null, null, null, "1"))));
        var actor = new AccessActor(1L, "tester", Set.of("ORG-008"));
        var start = OffsetDateTime.parse("2026-09-01T09:00:00+08:00");
        var end = OffsetDateTime.parse("2026-09-22T09:00:00+08:00");

        var created = service.start(new StartMasterDataBatchRequest("SYNTHETIC-REQUEST-20260922", "ORG-008",
                ParameterEnvironment.TEST, MasterDataCategory.MEDICAL_DIRECTORY, MasterDataSyncMode.TIME_RANGE,
                start, end), actor);
        service.run(created.id(), new byte[8], actor);

        assertEquals("ORG-008", persisted.get().sourceOrganizationId());
        verify(his, times(4)).countMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), argThat(query ->
                query.sourceOrganizationCode().equals("ORG-008") && query.rangeStart().equals(start.toLocalDateTime())
                        && query.rangeEnd().equals(end.toLocalDateTime())));
        verify(his, times(4)).queryMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), argThat(query ->
                query.sourceOrganizationCode().equals("ORG-008") && query.rangeStart().equals(start.toLocalDateTime())
                        && query.startRow() == 1 && query.endRow() == 1));
        verify(directoryMapper, times(4)).insertDirectoryDirect(eq(25L), eq(8L), any());
        verify(directoryMapper).finishCompleted(eq(25L), eq("COMPLETED"),
                argThat(counts -> counts.returned() == 4 && counts.created() == 4), eq(null), eq(null), eq("tester"));
        verify(records, times(8)).record(argThat(record -> record.sourceRecordId().equals(created.batchNo())
                && record.organizationCode().equals("ORG-008")));
    }
}
