package cn.zqkj.platform.system.domain.vo;

import cn.zqkj.platform.framework.security.PlatformUserPrincipal;

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
 * @param organizationCodes 当前显式机构范围代码
 */
public record CurrentUserVO(
        long userId,
        String loginName,
        String displayName,
        long primaryOrganizationId,
        String organizationCode,
        boolean mustChangePassword,
        List<String> permissions,
        List<String> organizationCodes
) {

    /**
     * 从服务端认证主体创建脱敏当前用户视图。
     *
     * @param principal 平台认证主体
     * @return 当前用户视图
     */
    public static CurrentUserVO from(PlatformUserPrincipal principal) {
        return new CurrentUserVO(
                principal.userId(),
                principal.getUsername(),
                principal.displayName(),
                principal.primaryOrganizationId(),
                principal.organizationCode(),
                principal.mustChangePassword(),
                principal.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .filter(authority -> !authority.startsWith("ORG:"))
                        .sorted()
                        .toList(),
                principal.organizationCodes().stream().sorted().toList()
        );
    }

    /**
     * 防止调用方持有可变权限列表。
     */
    public CurrentUserVO {
        permissions = List.copyOf(permissions);
        organizationCodes = List.copyOf(organizationCodes);
    }
}
