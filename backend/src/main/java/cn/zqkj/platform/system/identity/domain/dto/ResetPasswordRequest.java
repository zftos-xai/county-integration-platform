package cn.zqkj.platform.system.identity.domain.dto;

import cn.zqkj.platform.framework.security.validation.PasswordSize;

import jakarta.validation.constraints.NotBlank;

/**
 * 承载管理员重置临时密码请求。
 *
 * @param temporaryPassword 新临时密码
 */
public record ResetPasswordRequest(@NotBlank @PasswordSize String temporaryPassword) {
}
