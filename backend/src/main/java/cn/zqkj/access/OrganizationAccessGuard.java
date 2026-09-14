package cn.zqkj.access;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class OrganizationAccessGuard {

    private static final String AUTHORITY_PREFIX = "ORG:";

    public void requireAccess(Authentication authentication, String organizationCode) {
        String requiredAuthority = AUTHORITY_PREFIX + organizationCode;
        boolean permitted = authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> requiredAuthority.equals(authority.getAuthority()));
        if (!permitted) {
            throw new AccessDeniedException("Organization access is not permitted");
        }
    }
}
