package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.constraints.NotBlank;

/** @param version Base64编码并发版本 */
public record DeleteDictionaryRequest(@NotBlank String version) {
}
