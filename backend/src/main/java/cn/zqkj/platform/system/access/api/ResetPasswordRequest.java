package cn.zqkj.platform.system.access.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 承载管理员重置临时密码请求。
 *
 * @param temporaryPassword 新临时密码
 */
public record ResetPasswordRequest(@NotBlank @Size(min = 12, max = 128) String temporaryPassword) {
}
