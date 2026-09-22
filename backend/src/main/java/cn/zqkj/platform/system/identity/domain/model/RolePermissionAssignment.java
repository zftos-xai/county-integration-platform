package cn.zqkj.platform.system.identity.domain.model;


/**
 * 批量查询角色功能权限所需的最小投影。
 *
 * <p>数据来源：{@code dbo.sys_role_permission}（角色权限授权关系表）。
 * 业务说明：将列表中的角色主键关联到功能权限代码，不对应完整表行。</p>
 *
 * @param roleId 平台角色主键
 * @param permissionCode 已授予的功能权限代码
 */
public record RolePermissionAssignment(long roleId, String permissionCode) {
}
