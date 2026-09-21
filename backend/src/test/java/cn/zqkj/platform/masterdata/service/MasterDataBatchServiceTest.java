package cn.zqkj.platform.masterdata.service;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.masterdata.domain.dto.StartMasterDataBatchRequest;
import cn.zqkj.platform.masterdata.domain.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.model.MasterDataScopeType;
import cn.zqkj.platform.masterdata.domain.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.masterdata.mapper.MasterDataBatchMapper;
import cn.zqkj.platform.masterdata.service.impl.MasterDataBatchServiceImpl;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.domain.model.ExternalEndpointRuntimeConfiguration;
import cn.zqkj.platform.system.domain.model.ExternalEndpointScope;
import cn.zqkj.platform.system.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.domain.model.ExternalEndpointAuthentication;
import cn.zqkj.platform.system.domain.model.ExternalEndpointVerificationStatus;
import cn.zqkj.platform.system.service.ExternalEndpointResolutionService;
import cn.zqkj.platform.system.service.ManagementAuditService;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证同步批次创建时的业务范围和来源条件。
 */
class MasterDataBatchServiceTest {

    /** 验证发起选项由真实HIS范围和后端已实现业务共同生成。 */
    @Test
    void returnsConfiguredSourcesAndImplementedBusinesses() {
        MasterDataBatchMapper mapper = mock(MasterDataBatchMapper.class);
        ExternalEndpointResolutionService endpointService = mock(ExternalEndpointResolutionService.class);
        when(endpointService.findAvailableScopes("PRIMARY_HIS", List.of("ORG001")))
                .thenReturn(List.of(new ExternalEndpointScope(
                        10L, "ORG001", "测试机构", ParameterEnvironment.PRODUCTION)));
        MasterDataBatchService service = new MasterDataBatchServiceImpl(
                mapper, mock(ManagementAuditService.class), endpointService);

        var result = service.findSyncOptions(actor());

        assertEquals("ORG001", result.sources().get(0).organizationCode());
        assertEquals(MasterDataCategory.HOSPITAL_DIRECTORY, result.businesses().get(0).category());
        assertEquals("100-003", result.businesses().get(0).tradeCode());
    }

    /** 验证医院综合目录同步不让调用方选择100-003的某一个目录类型。 */
    @Test
    void createsHospitalDirectoryBatchWithoutSourceType() {
        MasterDataBatchMapper mapper = mock(MasterDataBatchMapper.class);
        ExternalEndpointResolutionService endpointService = availableEndpointService();
        MasterDataBatchService service = new MasterDataBatchServiceImpl(
                mapper, mock(ManagementAuditService.class), endpointService);
        when(mapper.findEnabledOrganizationId("ORG001")).thenReturn(10L);
        when(mapper.create(any(), any(), any(), anyLong(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any())).thenReturn(25L);
        when(mapper.findById(25L)).thenReturn(snapshot());

        service.start(new StartMasterDataBatchRequest(
                "8BCDCA4E11A44A9D888D7E70", "ORG001", ParameterEnvironment.PRODUCTION,
                MasterDataCategory.HOSPITAL_DIRECTORY
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
        MasterDataBatchService service = new MasterDataBatchServiceImpl(
                mapper, mock(ManagementAuditService.class), endpointService);
        when(mapper.findEnabledOrganizationId("ORG001")).thenReturn(10L);

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> service.start(new StartMasterDataBatchRequest(
                        "8BCDCA4E11A44A9D888D7E70", "ORG001", ParameterEnvironment.PRODUCTION,
                        MasterDataCategory.HOSPITAL_DIRECTORY
                ), actor()));

        assertEquals("该机构的基层HIS配置未保存100-008来源机构结果，不能发起同步", exception.getMessage());
    }

    /** 验证尚未执行的批次可受控取消且不会删除历史记录。 */
    @Test
    void cancelsCreatedBatchWithOptimisticVersion() {
        MasterDataBatchMapper mapper = mock(MasterDataBatchMapper.class);
        MasterDataBatchService service = new MasterDataBatchServiceImpl(
                mapper, mock(ManagementAuditService.class), availableEndpointService());
        MasterDataBatchSnapshot created = snapshot();
        MasterDataBatchSnapshot cancelled = new MasterDataBatchSnapshot(
                25L, "BD-TEST", MasterDataScopeType.ORGANIZATION, "ORG001", "测试机构",
                ParameterEnvironment.PRODUCTION,
                MasterDataCategory.HOSPITAL_DIRECTORY, "100-003", null, null, "HIS-ORG-001", null, null,
                MasterDataBatchStatus.FAILED, null, 0, 0, 0, 0, 0, 0, 0, 0, null,
                null, null, null, "CANCELLED_BY_USER", "本批次已由业务人员取消，未调用来源HIS。", new byte[]{2}
        );
        when(mapper.findById(25L)).thenReturn(created, cancelled);
        when(mapper.cancel(25L, new byte[8], "admin")).thenReturn(1);

        MasterDataBatchSummaryVO result = service.cancel(25L, new byte[8], actor());

        assertEquals("CANCELLED_BY_USER", result.failureCode());
        verify(mapper).cancel(25L, new byte[8], "admin");
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
                25L, "BD-TEST", MasterDataScopeType.ORGANIZATION, "ORG001", "测试机构",
                ParameterEnvironment.PRODUCTION,
                MasterDataCategory.HOSPITAL_DIRECTORY, "100-003", null, null, "HIS-ORG-001", null, null,
                MasterDataBatchStatus.CREATED, null, 0, 0, 0, 0, 0, 0, 0, 0, null,
                null, null, null, null, null, new byte[8]
        );
    }
}
