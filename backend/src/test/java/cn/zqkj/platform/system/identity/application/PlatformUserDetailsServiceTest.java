package cn.zqkj.platform.system.identity.application;

import cn.zqkj.platform.foundation.security.PlatformUserPrincipal;
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
        IdentityRepository repository = mock(IdentityRepository.class);
        when(repository.findByLoginName("admin")).thenReturn(Optional.of(account(true)));
        PlatformUserDetailsService service = new PlatformUserDetailsService(repository);

        PlatformUserPrincipal principal = (PlatformUserPrincipal) service.loadUserByUsername(" ADMIN ");

        assertEquals(List.of("password:change"), principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).toList());
        verify(repository, never()).findPermissionCodes(30L);
    }

    /**
     * 验证完成改密的用户从启用角色装载代码权限。
     */
    @Test
    void loadsRegisteredPermissionsAfterPasswordChange() {
        IdentityRepository repository = mock(IdentityRepository.class);
        when(repository.findByLoginName("admin")).thenReturn(Optional.of(account(false)));
        when(repository.findPermissionCodes(30L)).thenReturn(List.of("organization:read", "organization:write"));
        when(repository.findOrganizationCodes(30L)).thenReturn(List.of("ORG001"));
        PlatformUserDetailsService service = new PlatformUserDetailsService(repository);

        PlatformUserPrincipal principal = (PlatformUserPrincipal) service.loadUserByUsername("admin");

        assertEquals(3, principal.getAuthorities().size());
    }

    /**
     * 创建指定首次改密状态的用户快照。
     *
     * @param mustChangePassword 是否必须修改密码
     * @return 用户快照
     */
    private UserAccount account(boolean mustChangePassword) {
        return new UserAccount(
                30L, "admin", "平台管理员", "hash", 10L, "ORG001", true, mustChangePassword
        );
    }
}
