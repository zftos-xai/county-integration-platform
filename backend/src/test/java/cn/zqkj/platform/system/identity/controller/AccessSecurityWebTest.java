package cn.zqkj.platform.system.identity.controller;

import cn.zqkj.platform.common.exception.GlobalExceptionHandler;
import cn.zqkj.platform.framework.config.SecurityConfiguration;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.identity.domain.model.UserAccount;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import cn.zqkj.platform.system.identity.service.RoleAdministrationService;
import cn.zqkj.platform.system.identity.service.UserAdministrationService;
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
    private IdentityMapper identityMapper;

    @MockitoBean
    private UserDetailsService userDetailsService;

    /**
     * 角色与机构列表中的空元素、非正主键和过短密码都在入口拒绝。
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void validatesRelationshipIdsAndPasswordBeforeService() throws Exception {
        prepareActiveAccount("access:write");
        for (String element : List.of("null", "0", "-1")) {
            mockMvc.perform(MockMvcRequestBuilders
                            .put("/api/v1/users/2/roles").with(user(principal("access:write")))
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roleIds\":[" + element + "]}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(MockMvcRequestBuilders
                            .put("/api/v1/users/2/organization-scopes").with(user(principal("access:write")))
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"organizationIds\":[" + element + "]}"))
                    .andExpect(status().isBadRequest());
        }
        prepareActiveAccount("identity:write");
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/v1/users/2/password-reset").with(user(principal("identity:write")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temporaryPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(userAdministrationService, roleAdministrationService);
    }

    /**
     * 验证具有读取权限和机构范围的用户可进入用户查询服务。
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
        when(identityMapper.findById(1L)).thenReturn(
                new UserAccount(1L, "admin", "Administrator", "hash", 10L, "ORG001", true, false, new byte[8])
        );
        when(identityMapper.findPermissionCodes(1L)).thenReturn(List.of());
        when(identityMapper.findOrganizationCodes(1L)).thenReturn(List.of("ORG001"));

        mockMvc.perform(get("/api/v1/users").with(user(principal)))
                .andExpect(status().isUnauthorized());
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
