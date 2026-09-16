package cn.zqkj.platform.system.domain.vo;

/**
 * 表示后端代码注册、可供角色选择的功能权限。
 *
 * @param permissionCode 权限代码
 * @param permissionName 权限名称
 */
public record PermissionVO(String permissionCode, String permissionName) {
}
