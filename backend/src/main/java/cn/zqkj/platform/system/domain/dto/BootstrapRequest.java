package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 承载一次性平台安全引导请求。
 *
 * @param organizationCode 首个机构代码
 * @param organizationName 首个机构名称
 * @param organizationType 经确认的机构类型代码
 * @param loginName 初始管理员登录名
 * @param displayName 初始管理员显示名称
 * @param initialPassword 初始管理员一次性密码
 */
public record BootstrapRequest(
        @NotBlank @Size(max = 64) String organizationCode,
        @NotBlank @Size(max = 200) String organizationName,
        @NotBlank @Size(max = 32) String organizationType,
        @NotBlank @Size(min = 3, max = 64) String loginName,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(min = 12, max = 128) String initialPassword
) {
}
