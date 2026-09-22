package cn.zqkj.platform.system.identity.domain.model;


/**
 * 批量查询用户机构范围所需的最小投影。
 *
 * <p>数据来源：{@code dbo.sys_user_organization_scope}（用户机构范围授权关系表）。
 * 业务说明：将列表中的用户主键关联到显式授权的机构，不对应完整表行。</p>
 *
 * @param userId 平台用户主键
 * @param organizationId 已授权机构主键
 */
public record UserOrganizationAssignment(long userId, long organizationId) {
}
