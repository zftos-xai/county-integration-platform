package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 承载创建平台用户请求。
 *
 * @param loginName 登录名
 * @param displayName 显示名称
 * @param primaryOrganizationId 主机构主键
 * @param temporaryPassword 一次性临时密码
 */
public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 64) String loginName,
        @NotBlank @Size(max = 100) String displayName,
        @Positive long primaryOrganizationId,
        @NotBlank @Size(min = 12, max = 128) String temporaryPassword
) {
}
