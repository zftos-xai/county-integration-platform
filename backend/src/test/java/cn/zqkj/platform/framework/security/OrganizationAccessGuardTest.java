package cn.zqkj.platform.framework.security;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证机构数据范围守卫的默认拒绝规则。
 */
class OrganizationAccessGuardTest {

    private final OrganizationAccessGuard guard = new OrganizationAccessGuard();

    /**
     * 验证用户只能访问权限标识完全匹配的机构。
     */
    @Test
    void permitsOnlyMatchingOrganizationAuthority() {
        PlatformUserPrincipal principal = principal(List.of(new SimpleGrantedAuthority("ORG:ORG001")));
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            assertDoesNotThrow(() -> guard.requireAccess("ORG001"));
            assertThrows(AccessDeniedException.class, () -> guard.requireAccess("ORG002"));
            assertEquals(List.of("ORG001"), guard.allowedOrganizationCodes(null));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** 验证没有机构授权时列表查询得到空范围，未认证调用被拒绝。 */
    @Test
    void deniesUnauthenticatedCallAndKeepsEmptyScope() {
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> guard.allowedOrganizationCodes(null));
        PlatformUserPrincipal principal = principal(List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            assertEquals(List.of(), guard.allowedOrganizationCodes(null));
            assertThrows(AccessDeniedException.class, () -> guard.allowedOrganizationCodes("ORG001"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 创建测试用已登录用户。
     *
     * @param authorities 模拟会话授权
     * @return 测试登录主体
     */
    private PlatformUserPrincipal principal(List<SimpleGrantedAuthority> authorities) {
        return new PlatformUserPrincipal(1L, "operator", "操作员", null, 10L, "ORG001", true, false, authorities, new byte[8]);
    }
}
