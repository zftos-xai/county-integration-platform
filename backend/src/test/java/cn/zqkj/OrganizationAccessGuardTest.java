package cn.zqkj;

import cn.zqkj.access.OrganizationAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrganizationAccessGuardTest {

    private final OrganizationAccessGuard guard = new OrganizationAccessGuard();

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
