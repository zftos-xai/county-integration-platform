package cn.zqkj.platform.foundation.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证机构数据范围守卫的默认拒绝规则。
 */
class OrganizationAccessGuardTest {

    private final OrganizationAccessGuard guard = new OrganizationAccessGuard();

    /**
     * 验证主体只能访问权限标识完全匹配的机构。
     */
    @Test
    void permitsOnlyMatchingOrganizationAuthority() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "operator",
                "unused",
                List.of(new SimpleGrantedAuthority("ORG:ORG001"))
        );

        assertDoesNotThrow(() -> guard.requireAccess(authentication, "ORG001"));
        assertThrows(AccessDeniedException.class, () -> guard.requireAccess(authentication, "ORG002"));
    }
}
