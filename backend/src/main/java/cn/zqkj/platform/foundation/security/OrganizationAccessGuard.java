package cn.zqkj.platform.foundation.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 校验当前登录主体是否拥有指定机构的数据访问权限。
 *
 * <p>该守卫只负责机构范围判定，不负责业务功能授权或患者数据用途审批。</p>
 */
@Component
public class OrganizationAccessGuard {

    private static final String AUTHORITY_PREFIX = "ORG:";

    /**
     * 要求当前主体持有目标机构对应的权限标识。
     *
     * @param authentication Spring Security 当前认证信息；允许为空并按拒绝处理
     * @param organizationCode 平台统一机构代码
     * @throws AccessDeniedException 未认证或没有目标机构权限时抛出
     */
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
