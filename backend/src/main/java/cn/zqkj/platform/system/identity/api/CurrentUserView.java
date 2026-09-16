package cn.zqkj.platform.system.identity.api;

import cn.zqkj.platform.foundation.security.PlatformUserPrincipal;

import java.util.List;

/**
 * 表示可返回管理端的当前已认证主体，不包含密码哈希和会话标识。
 *
 * @param userId 用户主键
 * @param loginName 登录名
 * @param displayName 显示名称
 * @param primaryOrganizationId 主机构主键
 * @param organizationCode 主机构代码
 * @param mustChangePassword 是否必须先修改密码
 * @param permissions 当前有效权限代码
 */
public record CurrentUserView(
        long userId,
        String loginName,
        String displayName,
        long primaryOrganizationId,
        String organizationCode,
        boolean mustChangePassword,
        List<String> permissions
) {

    /**
     * 从服务端认证主体创建脱敏当前用户视图。
     *
     * @param principal 平台认证主体
     * @return 当前用户视图
     */
    public static CurrentUserView from(PlatformUserPrincipal principal) {
        return new CurrentUserView(
                principal.userId(),
                principal.getUsername(),
                principal.displayName(),
                principal.primaryOrganizationId(),
                principal.organizationCode(),
                principal.mustChangePassword(),
                principal.getAuthorities().stream().map(authority -> authority.getAuthority()).sorted().toList()
        );
    }

    /**
     * 防止调用方持有可变权限列表。
     */
    public CurrentUserView {
        permissions = List.copyOf(permissions);
    }
}
