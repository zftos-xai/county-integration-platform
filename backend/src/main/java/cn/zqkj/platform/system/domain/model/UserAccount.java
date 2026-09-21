package cn.zqkj.platform.system.domain.model;

/**
 * 平台本地用户的认证只读投影。
 *
 * <p>数据来源：主表 {@code dbo.sys_user}（平台用户表），关联
 * {@code dbo.org_organization}（机构表）补充主归属机构代码并合并账号与机构的可用状态。</p>
 *
 * <p>业务说明：仅用于服务端登录认证和会话主体构建；密码哈希不得进入API响应、
 * 审计摘要或运行日志。</p>
 *
 * @param id 用户内部主键
 * @param loginName 本地登录名
 * @param displayName 显示名称
 * @param passwordHash BCrypt密码哈希
 * @param primaryOrganizationId 主归属机构主键
 * @param organizationCode 主归属机构代码
 * @param enabled 用户与主机构是否均可用
 * @param mustChangePassword 是否必须修改初始密码
 */
public record UserAccount(
        long id,
        String loginName,
        String displayName,
        String passwordHash,
        long primaryOrganizationId,
        String organizationCode,
        boolean enabled,
        boolean mustChangePassword
) {
}
