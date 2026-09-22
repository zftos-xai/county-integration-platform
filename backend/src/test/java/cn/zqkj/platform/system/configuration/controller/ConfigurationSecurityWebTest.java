package cn.zqkj.platform.system.configuration.controller;

import cn.zqkj.platform.common.exception.GlobalExceptionHandler;
import cn.zqkj.platform.framework.config.SecurityConfiguration;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.configuration.service.ConfigurationService;
import cn.zqkj.platform.system.identity.domain.model.UserAccount;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证平台配置API的功能权限和当前会话权限边界。
 */
@WebMvcTest(controllers = ConfigurationController.class)
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class})
class ConfigurationSecurityWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfigurationService configurationService;

    @MockitoBean
    private IdentityMapper identityMapper;

    @MockitoBean
    private UserDetailsService userDetailsService;

    /**
     * 缺失、空白和超长参数值在进入配置业务前返回 400。
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void rejectsInvalidParameterValueBeforeService() throws Exception {
        prepareActiveAccount("configuration:write");
        for (String value : List.of("null", "\"\"", "\"   \"", "\"" + "x".repeat(1001) + "\"")) {
            mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/configuration/parameters/test.key")
                            .with(user(principal("configuration:write")))
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"environment\":\"PRODUCTION\",\"enabled\":true,\"value\":" + value + "}"))
                    .andExpect(status().isBadRequest());
        }
        Mockito.verifyNoInteractions(configurationService);
    }

    /**
     * 参数键和值在 HTTP 入口完成文本整理，并直接传递同一个写入对象。
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void bindsNormalizedParameterWithoutRequestCopy() throws Exception {
        prepareActiveAccount("configuration:write");
        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/configuration/parameters/TEST.KEY")
                        .with(user(principal("configuration:write")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"environment\":\"PRODUCTION\",\"value\":\" true \",\"enabled\":true}"))
                .andExpect(status().isOk());
        Mockito.verify(configurationService).upsertParameter(Mockito.eq("test.key"),
                Mockito.argThat(command -> "true".equals(command.value())), Mockito.any());
    }

    /** 合法参数值仍须明确启停目标，缺失状态不能被静默绑定为 false。 */
    @Test
    void rejectsMissingEnabledBeforeService() throws Exception {
        prepareActiveAccount("configuration:write");
        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/configuration/parameters/test.key")
                        .with(user(principal("configuration:write")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"environment\":\"PRODUCTION\",\"value\":\"true\"}"))
                .andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(configurationService);
    }

    /**
     * 地址、超时和初次认证格式不合法时在 HTTP 入口拒绝，不能先查询或保存系统配置。
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void rejectsInvalidEndpointInputBeforeService() throws Exception {
        prepareActiveAccount("configuration:write");
        String template = "{\"environment\":\"TEST\",\"baseUrl\":\"%s\","
                + "\"connectTimeoutMs\":3000,\"readTimeoutMs\":15000,"
                + "\"authentication\":{\"vendorCode\":\"V01\",\"authorizationCode\":\"AUTH\"}}";
        for (String url : List.of("ftp://his.invalid/api", "http://user:pass@his.invalid/api",
                "http://his.invalid/api?foo=bar", "http://his.invalid/api#fragment", "http:///api", "not a URI")) {
            mockMvc.perform(MockMvcRequestBuilders
                            .post("/api/v1/configuration/external-systems/1/endpoints")
                            .with(user(principal("configuration:write")))
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(template.formatted(url)))
                    .andExpect(status().isBadRequest());
        }
        String valid = template.formatted("http://his.invalid/api");
        for (String invalid : List.of(valid.replace("15000", "1000"), valid.replace("V01", " "),
                valid.replace("3000", "1"))) {
            mockMvc.perform(MockMvcRequestBuilders
                            .post("/api/v1/configuration/external-systems/1/endpoints")
                            .with(user(principal("configuration:write")))
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        Mockito.verifyNoInteractions(configurationService);
    }

    /**
     * 端点更新的 Base64 版本必须恰好八字节，不将格式错误推迟到业务层。
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void validatesEndpointUpdateVersionAtHttpBoundary() throws Exception {
        prepareActiveAccount("configuration:write");
        String template = "{\"environment\":\"TEST\",\"baseUrl\":\"http://his.invalid/api\","
                + "\"connectTimeoutMs\":3000,\"readTimeoutMs\":15000,\"version\":\"%s\"}";
        for (String version : List.of("AQ==", "not-base64", "")) {
            mockMvc.perform(MockMvcRequestBuilders
                            .put("/api/v1/configuration/external-endpoints/9")
                            .with(user(principal("configuration:write")))
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(template.formatted(version)))
                    .andExpect(status().isBadRequest());
        }
        Mockito.verifyNoInteractions(configurationService);
    }

    /**
     * 验证具有配置读取权限的有效会话可以读取注册参数定义。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void permitsConfigurationReader() throws Exception {
        PlatformUserPrincipal principal = principal("configuration:read");
        prepareActiveAccount("configuration:read");
        when(configurationService.findParameterDefinitions()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/configuration/parameter-definitions").with(user(principal)))
                .andExpect(status().isOk());
        when(configurationService.findExternalSystems()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/configuration/external-systems").with(user(principal)))
                .andExpect(status().isOk());
    }

    /**
     * 验证只有机构范围而没有配置权限的用户被拒绝。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void rejectsUserWithoutConfigurationPermission() throws Exception {
        PlatformUserPrincipal principal = principal(null);
        prepareActiveAccount(null);

        mockMvc.perform(get("/api/v1/configuration/parameter-definitions").with(user(principal)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/configuration/external-systems").with(user(principal)))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证接入信息回显要求配置写权限，普通配置读取权限不能查看明文。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void protectsExternalEndpointAuthenticationWithWritePermission() throws Exception {
        PlatformUserPrincipal reader = principal("configuration:read");
        prepareActiveAccount("configuration:read");
        mockMvc.perform(get("/api/v1/configuration/external-endpoints/9/authentication").with(user(reader)))
                .andExpect(status().isForbidden());

        PlatformUserPrincipal writer = principal("configuration:write");
        prepareActiveAccount("configuration:write");
        mockMvc.perform(get("/api/v1/configuration/external-endpoints/9/authentication").with(user(writer)))
                .andExpect(status().isOk());
    }

    /**
     * 创建具有指定功能权限的测试登录主体。
     *
     * @param permission 可选功能权限
     * @return 测试用户
     */
    private PlatformUserPrincipal principal(String permission) {
        List<SimpleGrantedAuthority> authorities = permission == null
                ? List.of(new SimpleGrantedAuthority("ORG:ORG001"))
                : List.of(
                        new SimpleGrantedAuthority(permission),
                        new SimpleGrantedAuthority("ORG:ORG001")
                );
        return new PlatformUserPrincipal(
                1L, "admin", "Administrator", null, 10L, "ORG001", true, false, authorities, new byte[8]);
    }

    /**
     * 准备处于启用状态的测试账号。
     *
     * @param permission 可选数据库当前功能权限
     */
    private void prepareActiveAccount(String permission) {
        when(identityMapper.findById(1L)).thenReturn(
                new UserAccount(1L, "admin", "Administrator", "hash", 10L, "ORG001", true, false, new byte[8])
        );
        when(identityMapper.findPermissionCodes(1L)).thenReturn(
                permission == null ? List.of() : List.of(permission)
        );
        when(identityMapper.findOrganizationCodes(1L)).thenReturn(List.of("ORG001"));
    }
}
