package cn.zqkj.platform.system.configuration.controller;

import cn.zqkj.platform.common.exception.GlobalExceptionHandler;
import cn.zqkj.platform.framework.config.SecurityConfiguration;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.identity.domain.model.UserAccount;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import cn.zqkj.platform.system.configuration.service.ConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
                1L, "admin", "Administrator", null, 10L, "ORG001", true, false, authorities
        );
    }

    /**
     * 准备处于启用状态的测试账号。
     *
     * @param permission 可选数据库当前功能权限
     */
    private void prepareActiveAccount(String permission) {
        when(identityMapper.findById(1L)).thenReturn(
                new UserAccount(1L, "admin", "Administrator", "hash", 10L, "ORG001", true, false)
        );
        when(identityMapper.findPermissionCodes(1L)).thenReturn(
                permission == null ? List.of() : List.of(permission)
        );
        when(identityMapper.findOrganizationCodes(1L)).thenReturn(List.of("ORG001"));
    }
}
