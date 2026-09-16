package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 承载用户启停请求。
 *
 * @param enabled 目标状态
 * @param version Base64编码并发版本
 */
public record ChangeEnabledRequest(boolean enabled, @NotBlank String version) {
}
