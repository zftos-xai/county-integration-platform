package cn.zqkj.platform.system.identity.domain.model;


/**
 * 批量查询用户与角色关系所需的最小投影。
 *
 * <p>数据来源：{@code dbo.sys_user_role}（用户角色授权关系表）。
 * 业务说明：将列表中的用户主键关联到已授予的角色，不对应用户或角色的完整表行。</p>
 *
 * @param userId 平台用户主键
 * @param roleId 已授予的角色主键
 */
public record UserRoleAssignment(long userId, long roleId) {
}
