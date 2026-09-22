package cn.zqkj.platform.his.service.impl;

import cn.zqkj.platform.his.client.PhisProtocolClient;
import cn.zqkj.platform.his.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.his.domain.medicaldirectory.dto.MedicalDirectoryCountQuery;
import cn.zqkj.platform.his.domain.medicaldirectory.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.his.domain.organization.dto.OrganizationQuery;
import cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryEntry;
import cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryType;
import cn.zqkj.platform.his.domain.medicaldirectory.model.MedicalDirectoryEntry;
import cn.zqkj.platform.his.domain.medicaldirectory.model.MedicalDirectoryType;
import cn.zqkj.platform.his.domain.organization.model.OrganizationEntry;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisConfigurationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.his.exception.PhisRequestException;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointAuthentication;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointRuntimeConfiguration;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.configuration.service.ExternalEndpointResolutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证基层HIS业务服务的机构隔离、端点选择、结果转换和安全日志。 */
class PhisServiceImplTest {

    /** 验证强类型条件会转换为100-003参数，并自动加入当前机构授权码。 */
    @Test
    void buildsHospitalDirectoryRequestWithOrganizationCredential() throws Exception {
        PhisProtocolClient client = mock(PhisProtocolClient.class);
        when(client.queryHospitalDirectory(any(), any()))
                .thenReturn(successResponse(List.of()));
        PhisServiceImpl service = new PhisServiceImpl(configuredResolver(8L, "AUTH-008"), client);

        service.queryHospitalDirectory(8L, ParameterEnvironment.TEST,
                new HospitalDirectoryQuery(HospitalDirectoryType.DOCTOR, " 张医生 ", "HIS-ORG-01"));

        ArgumentCaptor<HospitalDirectoryQuery> query = ArgumentCaptor.forClass(HospitalDirectoryQuery.class);
        verify(client).queryHospitalDirectory(any(), query.capture());
        assertEquals(HospitalDirectoryType.DOCTOR, query.getValue().directoryType());
        assertEquals(" 张医生 ", query.getValue().directoryName());
        assertEquals("HIS-ORG-01", query.getValue().sourceOrganizationCode());
    }

    /** 验证100-003成功数据会转换为强类型目录条目。 */
    @Test
    void mapsHospitalDirectoryResponseToTypedEntries() throws Exception {
        PhisProtocolClient client = mock(PhisProtocolClient.class);
        HospitalDirectoryEntry entry = directoryEntry("D001", "内科", "NK");
        when(client.queryHospitalDirectory(any(), any()))
                .thenReturn(successResponse(List.of(entry)));
        PhisServiceImpl service = new PhisServiceImpl(configuredResolver(8L, "AUTH-008"), client);

        PhisResponse<List<HospitalDirectoryEntry>> response = service.queryHospitalDirectory(
                8L, ParameterEnvironment.TEST,
                new HospitalDirectoryQuery(HospitalDirectoryType.DEPARTMENT, null, null));

        assertTrue(response.success());
        assertEquals(1, response.data().size());
        assertEquals("D001", response.data().get(0).directoryCode());
        assertEquals("内科", response.data().get(0).directoryName());
        assertEquals("NK", response.data().get(0).mnemonicCode());
        assertNull(response.errorMessage());
    }

    /** 验证目标业务失败时只返回错误说明，不把原始JSON暴露给业务层。 */
    @Test
    void returnsTypedBusinessFailure() {
        PhisProtocolClient client = mock(PhisProtocolClient.class);
        when(client.queryHospitalDirectory(any(), any()))
                .thenReturn(new PhisResponse<>(false, "0", null, "目录类型无效"));
        PhisServiceImpl service = new PhisServiceImpl(configuredResolver(8L, "AUTH-008"), client);

        PhisResponse<List<HospitalDirectoryEntry>> response = service.queryHospitalDirectory(
                8L, ParameterEnvironment.TEST,
                new HospitalDirectoryQuery(HospitalDirectoryType.DEPARTMENT, null, null));

        assertFalse(response.success());
        assertNull(response.data());
        assertEquals("目录类型无效", response.errorMessage());
    }

    /** 验证非法查询条件在外部调用前被拒绝。 */
    @Test
    void rejectsInvalidQueryBeforeExternalCall() {
        PhisProtocolClient client = mock(PhisProtocolClient.class);
        PhisServiceImpl service = new PhisServiceImpl(
                mock(ExternalEndpointResolutionService.class), client);

        assertThrows(PhisRequestException.class,
                () -> service.queryHospitalDirectory(8L, ParameterEnvironment.TEST,
                        new HospitalDirectoryQuery(null, null, null)));
        assertThrows(PhisRequestException.class,
                () -> service.queryHospitalDirectory(8L, ParameterEnvironment.TEST,
                        new HospitalDirectoryQuery(HospitalDirectoryType.DOCTOR, "一".repeat(21), null)));
        verifyNoInteractions(client);
    }

    /** 验证适配器报告协议错误时业务服务保持明确异常。 */
    @Test
    void rejectsInvalidHospitalDirectoryPayload() throws Exception {
        PhisProtocolClient client = mock(PhisProtocolClient.class);
        when(client.queryHospitalDirectory(any(), any()))
                .thenThrow(new PhisProtocolException("基层HIS医院综合目录缺少目录编码"));
        PhisServiceImpl service = new PhisServiceImpl(configuredResolver(8L, "AUTH-008"), client);

        assertThrows(PhisProtocolException.class,
                () -> service.queryHospitalDirectory(8L, ParameterEnvironment.TEST,
                        new HospitalDirectoryQuery(HospitalDirectoryType.DEPARTMENT, null, null)));
    }

    /** 验证日志包含交易、机构、结果和耗时，但不包含查询条件。 */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsClearSafeTransactionMetadata(CapturedOutput output) throws Exception {
        PhisProtocolClient client = mock(PhisProtocolClient.class);
        when(client.queryHospitalDirectory(any(), any()))
                .thenReturn(successResponse(List.of()));
        PhisServiceImpl service = new PhisServiceImpl(configuredResolver(8L, "AUTH-008"), client);

        service.queryHospitalDirectory(8L, ParameterEnvironment.TEST,
                new HospitalDirectoryQuery(HospitalDirectoryType.DOCTOR, "不应进入日志", null));

        assertTrue(output.getOut().contains("HIS交易｜100-003 医院综合目录查询"));
        assertTrue(output.getOut().contains("｜机构8/测试｜成功｜耗时"));
        assertFalse(output.getOut().contains("不应进入日志"));
    }

    /** 验证通信失败日志明确结果未知并且不输出目标地址。 */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsUnknownResultForCommunicationFailure(CapturedOutput output) {
        PhisProtocolClient client = mock(PhisProtocolClient.class);
        when(client.queryHospitalDirectory(any(), any()))
                .thenThrow(new PhisCommunicationException("http://his.example.invalid password=SYNTHETIC-SECRET\n正文"));
        PhisServiceImpl service = new PhisServiceImpl(
                configuredResolver(8L, "AUTH-008"), client);

        assertThrows(PhisCommunicationException.class,
                () -> service.queryHospitalDirectory(8L, ParameterEnvironment.TEST,
                        new HospitalDirectoryQuery(HospitalDirectoryType.BED, null, null)));

        assertTrue(output.getOut().contains("｜机构8/测试｜结果未知｜通信失败或超时｜耗时"));
        assertFalse(output.getOut().contains("his.example.invalid"));
        assertFalse(output.getOut().contains("SYNTHETIC-SECRET"));
    }

    /** 上游结果码同样不可信，业务失败日志不得回显任意文本。 */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void doesNotLogUntrustedResultCode(CapturedOutput output) {
        PhisProtocolClient client = mock(PhisProtocolClient.class);
        when(client.queryHospitalDirectory(any(), any()))
                .thenReturn(new PhisResponse<>(false, "SYNTHETIC-SECRET", null, "sensitive body"));
        new PhisServiceImpl(configuredResolver(8L, "AUTH-008"), client).queryHospitalDirectory(
                8L, ParameterEnvironment.TEST, new HospitalDirectoryQuery(HospitalDirectoryType.BED, null, null));
        assertTrue(output.getOut().contains("业务失败"));
        assertFalse(output.getOut().contains("SYNTHETIC-SECRET"));
        assertFalse(output.getOut().contains("sensitive body"));
    }

    /** 验证耗时格式按毫秒、秒和分秒分段展示。 */
    @Test
    void formatsTransactionDurationForOperators() {
        assertEquals("999毫秒", PhisServiceImpl.formatDuration(999));
        assertEquals("1秒", PhisServiceImpl.formatDuration(1_000));
        assertEquals("12.5秒", PhisServiceImpl.formatDuration(12_500));
        assertEquals("1分0秒", PhisServiceImpl.formatDuration(60_000));
        assertEquals("2分5秒", PhisServiceImpl.formatDuration(125_999));
    }

    /** 验证机构查询与配置校验共用调用流程，但分别解析已启用和待校验端点。 */
    @Test
    void selectsEndpointRequirementForOrganizationQuery() {
        ExternalEndpointResolutionService resolver = mock(ExternalEndpointResolutionService.class);
        ExternalEndpointRuntimeConfiguration runtime = runtimeConfiguration(8L, "AUTH-008");
        when(resolver.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, 8L))
                .thenReturn(Optional.of(runtime));
        when(resolver.findConfiguredRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, 8L))
                .thenReturn(Optional.of(runtime));
        PhisProtocolClient client = mock(PhisProtocolClient.class);
        PhisResponse<List<OrganizationEntry>> response = new PhisResponse<>(true, "1", List.of(), null);
        when(client.queryOrganizations(any(), any()))
                .thenReturn(response);
        PhisServiceImpl service = new PhisServiceImpl(resolver, client);
        OrganizationQuery query = new OrganizationQuery("县人民医院");

        service.queryOrganizations(8L, ParameterEnvironment.TEST, query);
        service.verifyOrganizationConfiguration(8L, ParameterEnvironment.TEST, query);

        verify(resolver).findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, 8L);
        verify(resolver).findConfiguredRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, 8L);
        verify(client, times(2)).queryOrganizations(any(), eq(query));
    }

    /** 验证100-004和100-005均通过统一上下文调用协议客户端。 */
    @Test
    void usesInvocationContextForMedicalDirectoryCalls() {
        PhisProtocolClient client = mock(PhisProtocolClient.class);
        when(client.queryMedicalDirectory(any(), any()))
                .thenReturn(PhisResponse.success("1", List.<MedicalDirectoryEntry>of()));
        when(client.countMedicalDirectory(any(), any()))
                .thenReturn(PhisResponse.success("1", 0L));
        PhisServiceImpl service = new PhisServiceImpl(configuredResolver(8L, "AUTH-008"), client);
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 2, 0, 0);

        service.queryMedicalDirectory(8L, ParameterEnvironment.TEST,
                new MedicalDirectoryQuery(MedicalDirectoryType.WESTERN_MEDICINE,
                        null, 1, 100, start, end, "ORG-008"));
        service.countMedicalDirectory(8L, ParameterEnvironment.TEST,
                new MedicalDirectoryCountQuery(MedicalDirectoryType.WESTERN_MEDICINE,
                        null, start, end, "ORG-008"));

        verify(client).queryMedicalDirectory(any(), any());
        verify(client).countMedicalDirectory(any(), any());
    }

    /** 验证本地查询错误记录为未发送，并且不会解析配置或调用HIS。 */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsRequestValidationFailureAsNotSent(CapturedOutput output) {
        ExternalEndpointResolutionService resolver = mock(ExternalEndpointResolutionService.class);
        PhisProtocolClient client = mock(PhisProtocolClient.class);
        PhisServiceImpl service = new PhisServiceImpl(resolver, client);

        assertThrows(PhisRequestException.class,
                () -> service.queryOrganizations(8L, ParameterEnvironment.TEST, null));

        assertTrue(output.getOut().contains("100-008 医疗机构信息查询"));
        assertTrue(output.getOut().contains("未发送"));
        verifyNoInteractions(resolver, client);
    }

    /** 验证不存在当前机构专属配置时拒绝调用。 */
    @Test
    void rejectsMissingOrMismatchedOrganizationConfiguration() {
        ExternalEndpointResolutionService missing = mock(ExternalEndpointResolutionService.class);
        when(missing.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, 8L))
                .thenReturn(Optional.empty());
        PhisServiceImpl missingService = new PhisServiceImpl(missing, mock(PhisProtocolClient.class));
        assertThrows(PhisConfigurationException.class,
                () -> missingService.queryHospitalDirectory(8L, ParameterEnvironment.TEST,
                        new HospitalDirectoryQuery(HospitalDirectoryType.WARD, null, null)));

        ExternalEndpointResolutionService wrong = mock(ExternalEndpointResolutionService.class);
        when(wrong.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, 9L))
                .thenReturn(Optional.of(runtimeConfiguration(8L, "AUTH-008")));
        PhisServiceImpl wrongService = new PhisServiceImpl(wrong, mock(PhisProtocolClient.class));
        assertThrows(PhisConfigurationException.class,
                () -> wrongService.queryHospitalDirectory(9L, ParameterEnvironment.TEST,
                        new HospitalDirectoryQuery(HospitalDirectoryType.DEPARTMENT, null, null)));
    }

    /**
     * 创建能够返回指定端点配置的解析服务替身。
     *
     * @param organizationId 机构主键
     * @param code 授权码
     * @return 已配置的模拟解析服务
     */
    private ExternalEndpointResolutionService configuredResolver(long organizationId, String code) {
        ExternalEndpointResolutionService resolver = mock(ExternalEndpointResolutionService.class);
        when(resolver.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, organizationId))
                .thenReturn(Optional.of(runtimeConfiguration(organizationId, code)));
        return resolver;
    }

    /**
     * 创建HIS成功响应。
     *
     * @param entries 目录数据
     * @return 客户端成功响应
     */
    private PhisResponse<List<HospitalDirectoryEntry>> successResponse(List<HospitalDirectoryEntry> entries) {
        return new PhisResponse<>(true, "1", entries, null);
    }

    /**
     * 创建字段完整的医院目录来源条目。
     *
     * @param code 目录编码
     * @param name 目录名称
     * @param mnemonic 助记码
     * @return 合成目录条目
     */
    private HospitalDirectoryEntry directoryEntry(String code, String name, String mnemonic) {
        return new HospitalDirectoryEntry(code, name, mnemonic, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * 创建不包含真实秘密的HIS运行配置。
     *
     * @param organizationId 机构主键
     * @param code 授权码
     * @return 合成运行配置
     */
    private ExternalEndpointRuntimeConfiguration runtimeConfiguration(long organizationId, String code) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 18, 0, 0);
        ExternalEndpoint endpoint = new ExternalEndpoint(
                3L, 2L, ParameterEnvironment.TEST, organizationId, "ORG" + organizationId,
                "http://his.example.invalid/WebService.asmx", 3000, 15000,
                "managed://database", true, true, now, now, new byte[]{1});
        return new ExternalEndpointRuntimeConfiguration(endpoint,
                new ExternalEndpointAuthentication("V01", "user", "password", code));
    }
}
