package cn.zqkj.platform.system.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 承载当前用户修改密码请求。
 *
 * @param currentPassword 当前密码
 * @param newPassword 新密码
 */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = 12, max = 128) String newPassword
) {
}
