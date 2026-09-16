package cn.zqkj.platform.system.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 承载平台本地会话登录请求。
 *
 * @param loginName 平台本地登录名
 * @param password 当前密码
 */
public record LoginRequest(
        @NotBlank @Size(max = 64) String loginName,
        @NotBlank @Size(max = 128) String password
) {
}
