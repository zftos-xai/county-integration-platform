package cn.zqkj.platform.system.configuration.service.impl;

import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointAuthentication;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointCredential;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointRuntimeConfiguration;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointScope;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.configuration.mapper.ConfigurationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证配置域统一解析外部系统地址和认证信息。
 */
class ExternalEndpointResolutionServiceImplTest {

    /** 验证平台托管密文由配置域解密后再提供给业务域。 */
    @Test
    void resolvesManagedAuthenticationInsideConfigurationDomain() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        ExternalEndpointCredentialCipher cipher = mock(ExternalEndpointCredentialCipher.class);
        ExternalEndpointCredential credential = new ExternalEndpointCredential(
                3L, new byte[]{1}, new byte[]{2}, 1);
        when(mapper.findEnabledExternalEndpoint("PRIMARY_HIS", ParameterEnvironment.TEST, 8L))
                .thenReturn(Optional.of(endpoint("managed://database")));
        when(mapper.findExternalEndpointCredential(3L)).thenReturn(Optional.of(credential));
        when(cipher.decrypt(credential)).thenReturn(
                new ExternalEndpointAuthentication("V01", null, null, "AUTH-008"));
        ExternalEndpointResolutionServiceImpl service = new ExternalEndpointResolutionServiceImpl(
                mapper, cipher, new ObjectMapper(), ignored -> null);

        var result = service.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, 8L).orElseThrow();

        assertEquals("AUTH-008", result.authentication().authorizationCode());
    }

    /** 验证兼容配置可按环境变量引用读取不同机构的认证信息。 */
    @Test
    void resolvesLegacyEnvironmentAuthentication() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        when(mapper.findEnabledExternalEndpoint("PRIMARY_HIS", ParameterEnvironment.TEST, 8L))
                .thenReturn(Optional.of(endpoint("env://PHIS_ORG_008")));
        Map<String, String> values = Map.of(
                "PHIS_ORG_008", "{\"vendorCode\":\"V01\",\"authorizationCode\":\"AUTH-008\"}"
        );
        ExternalEndpointResolutionServiceImpl service = new ExternalEndpointResolutionServiceImpl(
                mapper, mock(ExternalEndpointCredentialCipher.class), new ObjectMapper(), values::get);

        var result = service.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, 8L).orElseThrow();

        assertEquals("AUTH-008", result.authentication().authorizationCode());
    }

    /** 验证格式错误时不会把认证内容写入异常。 */
    @Test
    void doesNotExposeMalformedAuthenticationValue() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        when(mapper.findEnabledExternalEndpoint("PRIMARY_HIS", ParameterEnvironment.TEST, 8L))
                .thenReturn(Optional.of(endpoint("env://PHIS_ORG_008")));
        String sensitiveValue = "secret-authorization-code";
        ExternalEndpointResolutionServiceImpl service = new ExternalEndpointResolutionServiceImpl(
                mapper, mock(ExternalEndpointCredentialCipher.class), new ObjectMapper(),
                ignored -> "{broken:" + sensitiveValue);

        ResourceConflictException exception = assertThrows(ResourceConflictException.class,
                () -> service.findEnabledRuntime("PRIMARY_HIS", ParameterEnvironment.TEST, 8L));

        assertFalse(exception.getMessage().contains(sensitiveValue));
    }

    /** 验证运行配置和认证对象的字符串表示不会泄露地址、账号、密码或授权码。 */
    @Test
    void redactsRuntimeConfigurationStringRepresentations() {
        ExternalEndpointAuthentication authentication = new ExternalEndpointAuthentication(
                "V01", "sensitive-user", "sensitive-password", "sensitive-authorization-code");
        ExternalEndpointRuntimeConfiguration configuration =
                new ExternalEndpointRuntimeConfiguration(endpoint("managed://database"), authentication);

        assertEquals("ExternalEndpointAuthentication[REDACTED]", authentication.toString());
        assertEquals("ExternalEndpointRuntimeConfiguration[REDACTED]", configuration.toString());
        assertFalse(authentication.toString().contains("sensitive"));
        assertFalse(configuration.toString().contains("his.example.invalid"));
    }

    /** 验证可用范围会剔除无法解析认证信息的接口配置。 */
    @Test
    void listsOnlyScopesWithResolvableRuntimeConfiguration() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        ExternalEndpointCredentialCipher cipher = mock(ExternalEndpointCredentialCipher.class);
        ExternalEndpointCredential credential = new ExternalEndpointCredential(
                3L, new byte[]{1}, new byte[]{2}, 1);
        ExternalEndpointScope scope = new ExternalEndpointScope(
                8L, "ORG008", "测试机构", ParameterEnvironment.TEST);
        when(mapper.findEnabledExternalEndpointScopes("PRIMARY_HIS", List.of("ORG008")))
                .thenReturn(List.of(scope));
        when(mapper.findEnabledExternalEndpoint("PRIMARY_HIS", ParameterEnvironment.TEST, 8L))
                .thenReturn(Optional.of(endpoint("managed://database")));
        when(mapper.findExternalEndpointCredential(3L)).thenReturn(Optional.of(credential));
        when(cipher.decrypt(credential)).thenReturn(
                new ExternalEndpointAuthentication("V01", null, null, "AUTH-008"));
        ExternalEndpointResolutionServiceImpl service = new ExternalEndpointResolutionServiceImpl(
                mapper, cipher, new ObjectMapper(), ignored -> null);

        List<ExternalEndpointScope> result = service.findAvailableScopes(
                "PRIMARY_HIS", List.of("ORG008"));

        assertEquals(List.of(scope), result);
    }

    /**
     * 创建满足指定验证状态的端点测试快照。
     *
     * @param reference 认证信息引用
     * @return 测试接口配置
     */
    private ExternalEndpoint endpoint(String reference) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 18, 0, 0);
        return new ExternalEndpoint(3L, 2L, ParameterEnvironment.TEST, 8L, "ORG008",
                "http://his.example.invalid/WebService.asmx", 3000, 15000,
                reference, true, true, now, now, new byte[]{1});
    }
}
