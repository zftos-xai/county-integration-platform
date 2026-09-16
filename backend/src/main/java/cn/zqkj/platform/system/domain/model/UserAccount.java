package cn.zqkj.platform.system.domain.model;

/**
 * 表示用于认证和当前主体构建的平台本地用户快照。
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
