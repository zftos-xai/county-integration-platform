package cn.zqkj.platform.system.service;
import cn.zqkj.platform.system.service.impl.PlatformUserDetailsServiceImpl;

import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.domain.model.UserAccount;
import cn.zqkj.platform.system.mapper.IdentityMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证首次改密前后的权限装载规则。
 */
class PlatformUserDetailsServiceTest {

    /**
     * 验证初始密码用户只能取得修改密码权限。
     */
    @Test
    void restrictsInitialPasswordUser() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        when(mapper.findByLoginName("admin")).thenReturn(account(true));
        PlatformUserDetailsService service = new PlatformUserDetailsServiceImpl(mapper);

        PlatformUserPrincipal principal = (PlatformUserPrincipal) service.loadUserByUsername(" ADMIN ");

        assertEquals(List.of("password:change"), principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).toList());
        verify(mapper, never()).findPermissionCodes(30L);
    }

    /**
     * 验证完成改密的用户从启用角色装载代码权限。
     */
    @Test
    void loadsRegisteredPermissionsAfterPasswordChange() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        when(mapper.findByLoginName("admin")).thenReturn(account(false));
        when(mapper.findPermissionCodes(30L)).thenReturn(List.of("organization:read", "organization:write"));
        when(mapper.findOrganizationCodes(30L)).thenReturn(List.of("ORG001"));
        PlatformUserDetailsService service = new PlatformUserDetailsServiceImpl(mapper);

        PlatformUserPrincipal principal = (PlatformUserPrincipal) service.loadUserByUsername("admin");

        assertEquals(3, principal.getAuthorities().size());
    }

    /**
     * 创建指定首次改密状态的用户记录。
     *
     * @param mustChangePassword 是否必须修改密码
     * @return 用户记录
     */
    private UserAccount account(boolean mustChangePassword) {
        return new UserAccount(
                30L, "admin", "平台管理员", "hash", 10L, "ORG001", true, mustChangePassword
        );
    }
}
