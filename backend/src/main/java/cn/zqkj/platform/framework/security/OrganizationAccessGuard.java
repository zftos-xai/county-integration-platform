package cn.zqkj.platform.framework.security;

import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 从当前登录会话确定机构数据范围并校验显式机构筛选。
 *
 * <p>该守卫只负责机构范围判定，不负责业务功能授权或患者数据用途审批。</p>
 */
@Component
public class OrganizationAccessGuard {

    /**
     * 要求当前用户持有目标机构对应的权限标识。
     *
     * @param organizationCode 平台统一机构代码
     * @throws AccessDeniedException 未认证或没有目标机构权限时抛出
     */
    public void requireAccess(String organizationCode) {
        allowedOrganizationCodes(organizationCode);
    }

    /**
     * 从当前认证信息取得可查询机构范围，并拒绝范围外的显式机构筛选。
     *
     * @param requestedOrganizationCode 显式筛选机构；为空表示按全部获准机构查询
     * @return 已认证用户的机构代码集合；没有机构授权时返回空集合，由查询层拒绝全部行
     * @throws AccessDeniedException 未认证或显式筛选机构不在授权范围时抛出
     */
    public List<String> allowedOrganizationCodes(String requestedOrganizationCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PlatformUserPrincipal principal)) {
            throw new AccessDeniedException("当前账号无权访问该机构");
        }
        Set<String> organizationCodes = principal.organizationCodes();
        if (requestedOrganizationCode != null && !organizationCodes.contains(requestedOrganizationCode)) {
            throw new AccessDeniedException("当前账号无权访问该机构");
        }
        return List.copyOf(organizationCodes);
    }
}
