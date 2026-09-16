package cn.zqkj.platform.system.domain.vo;

/**
 * 表示安全引导成功后可公开的非敏感结果。
 *
 * @param userId 初始管理员内部主键
 * @param loginName 初始管理员登录名
 * @param organizationId 首个机构内部主键
 * @param organizationCode 首个机构代码
 * @param mustChangePassword 是否要求首次登录后修改密码
 */
public record BootstrapResultVO(
        long userId,
        String loginName,
        long organizationId,
        String organizationCode,
        boolean mustChangePassword
) {
}
