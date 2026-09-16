package cn.zqkj.platform.system.domain.dto;

/**
 * 承载创建平台用户的已解析输入。
 *
 * @param loginName 登录名
 * @param displayName 显示名称
 * @param primaryOrganizationId 主机构主键
 * @param temporaryPassword 临时密码明文，仅当前调用使用
 */
public record CreateUserCommand(
        String loginName,
        String displayName,
        long primaryOrganizationId,
        String temporaryPassword
) {
}
