package cn.zqkj.platform.system.access.api;

import cn.zqkj.platform.foundation.security.PlatformUserPrincipal;
import cn.zqkj.platform.foundation.security.SecurityConfiguration;
import cn.zqkj.platform.foundation.web.error.GlobalExceptionHandler;
import cn.zqkj.platform.system.access.application.RoleAdministrationService;
import cn.zqkj.platform.system.access.application.UserAdministrationService;
import cn.zqkj.platform.system.identity.application.IdentityRepository;
import cn.zqkj.platform.system.identity.application.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证用户角色管理API同时要求认证、功能权限和当前有效会话权限。
 */
@WebMvcTest(controllers = {UserAdministrationController.class, RoleAdministrationController.class})
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class})
class AccessSecurityWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAdministrationService userAdministrationService;

    @MockitoBean
    private RoleAdministrationService roleAdministrationService;

    @MockitoBean
    private IdentityRepository identityRepository;

    @MockitoBean
    private UserDetailsService userDetailsService;

    /**
     * 验证具有读取权限和机构范围的主体可进入用户查询服务。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void permitsIdentityReader() throws Exception {
        PlatformUserPrincipal principal = principal("identity:read");
        prepareActiveAccount("identity:read");
        when(userAdministrationService.findAll(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users").with(user(principal)))
                .andExpect(status().isOk());
    }

    /**
     * 验证只有机构范围而没有功能权限的主体被方法授权拒绝。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void rejectsUserWithoutIdentityPermission() throws Exception {
        PlatformUserPrincipal principal = principal(null);
        prepareActiveAccount(null);

        mockMvc.perform(get("/api/v1/users").with(user(principal)))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证数据库权限已经变化时旧会话在进入控制器前失效。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void invalidatesSessionAfterPermissionChange() throws Exception {
        PlatformUserPrincipal principal = principal("identity:read");
        when(identityRepository.findById(1L)).thenReturn(Optional.of(
                new UserAccount(1L, "admin", "Administrator", "hash", 10L, "ORG001", true, false)
        ));
        when(identityRepository.findPermissionCodes(1L)).thenReturn(List.of());
        when(identityRepository.findOrganizationCodes(1L)).thenReturn(List.of("ORG001"));

        mockMvc.perform(get("/api/v1/users").with(user(principal)))
                .andExpect(status().isUnauthorized());
    }

    /** @param permission 可选功能权限 @return 测试主体 */
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

    /** @param permission 可选数据库当前功能权限 */
    private void prepareActiveAccount(String permission) {
        when(identityRepository.findById(1L)).thenReturn(Optional.of(
                new UserAccount(1L, "admin", "Administrator", "hash", 10L, "ORG001", true, false)
        ));
        when(identityRepository.findPermissionCodes(1L)).thenReturn(
                permission == null ? List.of() : List.of(permission)
        );
        when(identityRepository.findOrganizationCodes(1L)).thenReturn(List.of("ORG001"));
    }
}
