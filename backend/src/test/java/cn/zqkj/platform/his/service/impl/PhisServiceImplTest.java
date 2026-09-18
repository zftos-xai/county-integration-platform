package cn.zqkj.platform.his.service.impl;

import cn.zqkj.platform.his.client.PhisClient;
import cn.zqkj.platform.his.domain.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisConfigurationException;
import cn.zqkj.platform.system.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.domain.model.ExternalEndpointAuthentication;
import cn.zqkj.platform.system.domain.model.ExternalEndpointRuntimeConfiguration;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.service.ExternalEndpointResolutionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证基层HIS服务只使用当前机构的运行配置和认证信息。
 */
class PhisServiceImplTest {

    /** 验证业务参数中的外来验证码会被当前机构配置覆盖。 */
    @Test
    void overwritesUntrustedAuthorizationCodeWithOrganizationCredential() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ExternalEndpointResolutionService resolver = mock(ExternalEndpointResolutionService.class);
        PhisClient client = mock(PhisClient.class);
        ExternalEndpointRuntimeConfiguration runtime = runtimeConfiguration(8L, "AUTH-008");
        when(resolver.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, 8L))
                .thenReturn(Optional.of(runtime));
        when(client.invoke(any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(new PhisResponse(true, "1", objectMapper.nullNode()));
        PhisServiceImpl service = new PhisServiceImpl(resolver, client, objectMapper);
        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("验证码", "WRONG-CODE");
        parameters.put("医院名称", "测试机构");

        service.invokeAuthorized(8L, ParameterEnvironment.TEST, "100-008", parameters);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).invoke(
                eq(URI.create(runtime.endpoint().baseUrl())), eq(3000), eq(15000),
                eq("100-008"), jsonCaptor.capture());
        JsonNode sent = objectMapper.readTree(jsonCaptor.getValue());
        assertEquals("AUTH-008", sent.path("验证码").asText());
        assertEquals("WRONG-CODE", parameters.path("验证码").asText());
    }

    /** 验证100-001使用当前机构配置中的厂商编号。 */
    @Test
    void usesOrganizationVendorCodeForConnectionTest() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ExternalEndpointResolutionService resolver = mock(ExternalEndpointResolutionService.class);
        PhisClient client = mock(PhisClient.class);
        ExternalEndpointRuntimeConfiguration runtime = runtimeConfiguration(9L, "AUTH-009");
        when(resolver.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.PRODUCTION, 9L))
                .thenReturn(Optional.of(runtime));
        when(client.invoke(any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(new PhisResponse(true, "1", objectMapper.nullNode()));
        PhisServiceImpl service = new PhisServiceImpl(resolver, client, objectMapper);

        service.testConnection(9L, ParameterEnvironment.PRODUCTION);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).invoke(any(), eq(3000), eq(15000), eq("100-001"), jsonCaptor.capture());
        assertEquals("V01", objectMapper.readTree(jsonCaptor.getValue()).path("厂商编号").asText());
    }

    /** 验证成功调用日志包含交易名称、分类、结果和耗时，但不包含业务参数。 */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsClearSafeTransactionMetadata(CapturedOutput output) {
        ObjectMapper objectMapper = new ObjectMapper();
        ExternalEndpointResolutionService resolver = mock(ExternalEndpointResolutionService.class);
        PhisClient client = mock(PhisClient.class);
        when(resolver.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, 8L))
                .thenReturn(Optional.of(runtimeConfiguration(8L, "AUTH-008")));
        when(client.invoke(any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(new PhisResponse(true, "1", objectMapper.nullNode()));
        PhisServiceImpl service = new PhisServiceImpl(resolver, client, objectMapper);
        ObjectNode parameters = objectMapper.createObjectNode().put("患者姓名", "不应进入日志");

        service.invokeAuthorized(8L, ParameterEnvironment.TEST, "100-003", parameters);

        assertTrue(output.getOut().contains("HIS交易｜100-003 医院综合目录查询"));
        assertTrue(output.getOut().contains("｜机构8/测试｜成功(1)｜耗时"));
        assertTrue(output.getOut().contains("耗时"));
        assertTrue(!output.getOut().contains("不应进入日志"));
    }

    /** 验证通信失败日志明确结果未知并且不输出目标地址。 */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsUnknownResultForCommunicationFailure(CapturedOutput output) {
        ObjectMapper objectMapper = new ObjectMapper();
        ExternalEndpointResolutionService resolver = mock(ExternalEndpointResolutionService.class);
        PhisClient client = mock(PhisClient.class);
        when(resolver.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, 8L))
                .thenReturn(Optional.of(runtimeConfiguration(8L, "AUTH-008")));
        when(client.invoke(any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenThrow(new PhisCommunicationException("基层HIS调用超时，结果未知"));
        PhisServiceImpl service = new PhisServiceImpl(resolver, client, objectMapper);

        assertThrows(PhisCommunicationException.class,
                () -> service.invokeAuthorized(8L, ParameterEnvironment.TEST,
                        "500-002", objectMapper.createObjectNode()));

        assertTrue(output.getOut().contains("HIS交易｜500-002 按申请单号获取检查申请单"));
        assertTrue(output.getOut().contains("｜机构8/测试｜结果未知｜基层HIS调用超时｜耗时"));
        assertTrue(!output.getOut().contains("his.example.invalid"));
    }

    /** 验证一秒内使用毫秒，一分钟内使用秒，一分钟及以上使用分秒。 */
    @Test
    void formatsTransactionDurationForOperators() {
        assertEquals("0毫秒", PhisServiceImpl.formatDuration(0));
        assertEquals("125毫秒", PhisServiceImpl.formatDuration(125));
        assertEquals("999毫秒", PhisServiceImpl.formatDuration(999));
        assertEquals("1秒", PhisServiceImpl.formatDuration(1_000));
        assertEquals("12.5秒", PhisServiceImpl.formatDuration(12_500));
        assertEquals("59.999秒", PhisServiceImpl.formatDuration(59_999));
        assertEquals("1分0秒", PhisServiceImpl.formatDuration(60_000));
        assertEquals("2分5秒", PhisServiceImpl.formatDuration(125_999));
    }

    /** 验证不存在机构专属配置时不执行外部调用。 */
    @Test
    void rejectsMissingOrganizationConfigurationWithoutFallback() {
        ExternalEndpointResolutionService resolver = mock(ExternalEndpointResolutionService.class);
        when(resolver.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, 8L))
                .thenReturn(Optional.empty());
        PhisServiceImpl service = new PhisServiceImpl(resolver, mock(PhisClient.class), new ObjectMapper());

        assertThrows(PhisConfigurationException.class,
                () -> service.testConnection(8L, ParameterEnvironment.TEST));
    }

    /** 验证配置域异常返回其他机构记录时拒绝使用。 */
    @Test
    void rejectsConfigurationFromAnotherOrganization() {
        ExternalEndpointResolutionService resolver = mock(ExternalEndpointResolutionService.class);
        when(resolver.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, 9L))
                .thenReturn(Optional.of(runtimeConfiguration(8L, "AUTH-008")));
        PhisServiceImpl service = new PhisServiceImpl(resolver, mock(PhisClient.class), new ObjectMapper());

        assertThrows(PhisConfigurationException.class,
                () -> service.testConnection(9L, ParameterEnvironment.TEST));
    }

    /** @param organizationId 机构主键 @param authorizationCode 授权码 @return 合成运行配置 */
    private ExternalEndpointRuntimeConfiguration runtimeConfiguration(
            long organizationId,
            String authorizationCode
    ) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 18, 0, 0);
        ExternalEndpoint endpoint = new ExternalEndpoint(
                3L, 2L, ParameterEnvironment.TEST, organizationId, "ORG" + organizationId,
                "http://his.example.invalid/WebService.asmx", 3000, 15000,
                "managed://database", true, true, now, now, new byte[]{1});
        return new ExternalEndpointRuntimeConfiguration(
                endpoint,
                new ExternalEndpointAuthentication("V01", "user", "password", authorizationCode)
        );
    }
}
