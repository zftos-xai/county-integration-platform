package cn.zqkj.platform.system.identity.domain.vo;

import cn.zqkj.platform.framework.security.PlatformUserPrincipal;

import java.util.List;

/**
 * 表示可返回管理端的当前已登录用户，不包含密码哈希和会话标识。
 *
 * @param userId 用户主键
 * @param loginName 登录名
 * @param displayName 显示名称
 * @param primaryOrganizationId 主机构主键
 * @param organizationCode 主机构代码
 * @param organizationName 主归属机构名称，仅供辨认身份
 * @param roleNames 当前获授的已启用角色名称，仅供展示，不作为授权依据
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
        String organizationName,
        List<String> roleNames,
        boolean mustChangePassword,
        List<String> permissions,
        List<String> organizationCodes
) {

    /**
     * 从服务端登录用户创建脱敏当前用户视图。
     *
     * @param principal 平台登录用户
     * @param organizationName 主机构名称
     * @param roleNames 已启用角色名称
     * @return 当前用户视图
     */
    public static CurrentUserVO from(PlatformUserPrincipal principal, String organizationName, List<String> roleNames) {
        return new CurrentUserVO(
                principal.userId(),
                principal.getUsername(),
                principal.displayName(),
                principal.primaryOrganizationId(),
                principal.organizationCode(),
                organizationName,
                roleNames,
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
     *
     * @param userId 用户主键
     * @param loginName 登录名
     * @param displayName 显示名称
     * @param primaryOrganizationId 主机构主键
     * @param organizationCode 主机构代码
     * @param organizationName 主归属机构名称
     * @param roleNames 已启用角色名称
     * @param mustChangePassword 是否必须先修改密码
     * @param permissions 当前有效权限代码
     * @param organizationCodes 当前显式机构范围代码
     */
    public CurrentUserVO {
        roleNames = List.copyOf(roleNames);
        permissions = List.copyOf(permissions);
        organizationCodes = List.copyOf(organizationCodes);
    }
}
