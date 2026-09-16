package cn.zqkj.platform.system.domain.dto;

/**
 * 承载一次性平台安全引导的已接收输入。
 *
 * @param organizationCode 首个受控平台机构代码
 * @param organizationName 首个受控平台机构名称
 * @param organizationType 经项目确认的机构类型代码
 * @param loginName 初始管理员登录名
 * @param displayName 初始管理员显示名称
 * @param initialPassword 只在当前调用内使用的初始密码明文
 */
public record BootstrapCommand(
        String organizationCode,
        String organizationName,
        String organizationType,
        String loginName,
        String displayName,
        String initialPassword
) {
}
