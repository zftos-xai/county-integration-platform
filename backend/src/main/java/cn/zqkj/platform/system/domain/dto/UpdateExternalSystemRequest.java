package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param systemName 名称 @param description 用途说明 @param enabled 是否启用 @param version Base64并发版本 */
public record UpdateExternalSystemRequest(@NotBlank @Size(max = 100) String systemName,
                                          @NotBlank @Size(max = 500) String description,
                                          boolean enabled, @NotBlank String version) {
}
