package cn.zqkj.platform.system.identity.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 承载用户启停请求。
 *
 * @param enabled 目标状态
 * @param version Base64编码并发版本
 */
public record ChangeEnabledRequest(@NotNull Boolean enabled, @NotBlank String version) {
}
