package cn.zqkj.platform.system.controller;

import cn.zqkj.platform.common.exception.GlobalExceptionHandler;
import cn.zqkj.platform.framework.config.SecurityConfiguration;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.domain.model.UserAccount;
import cn.zqkj.platform.system.mapper.IdentityMapper;
import cn.zqkj.platform.system.service.ManagementAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证管理审计查询只允许具有audit:read权限的有效主体。 */
@WebMvcTest(controllers = ManagementAuditController.class)
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class})
class ManagementAuditSecurityWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ManagementAuditService service;
    @MockitoBean private IdentityMapper identityMapper;
    @MockitoBean private UserDetailsService userDetailsService;

    /** @throws Exception MockMvc调用失败时抛出 */
    @Test
    void enforcesAuditReadPermission() throws Exception {
        prepareAccount(List.of("audit:read"));
        PlatformUserPrincipal reader = principal(List.of("audit:read"));
        when(service.findVisible(any(), isNull(), isNull(), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(List.of());
        mockMvc.perform(get("/api/v1/audit/events").with(user(reader))).andExpect(status().isOk());

        prepareAccount(List.of());
        mockMvc.perform(get("/api/v1/audit/events").with(user(principal(List.of()))))
                .andExpect(status().isForbidden());
    }

    /** @param permissions 当前权限 */
    private void prepareAccount(List<String> permissions) {
        when(identityMapper.findById(1L)).thenReturn(
                new UserAccount(1L, "admin", "Administrator", "hash", 10L, "ORG001", true, false)
        );
        when(identityMapper.findPermissionCodes(1L)).thenReturn(permissions);
        when(identityMapper.findOrganizationCodes(1L)).thenReturn(List.of("ORG001"));
    }

    /** @param permissions 权限 @return 测试主体 */
    private PlatformUserPrincipal principal(List<String> permissions) {
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        permissions.forEach(value -> authorities.add(new SimpleGrantedAuthority(value)));
        authorities.add(new SimpleGrantedAuthority("ORG:ORG001"));
        return new PlatformUserPrincipal(1L, "admin", "Administrator", null, 10L, "ORG001", true, false,
                authorities);
    }
}
