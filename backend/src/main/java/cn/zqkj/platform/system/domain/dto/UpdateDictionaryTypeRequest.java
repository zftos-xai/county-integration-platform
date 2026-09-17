package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改平台系统字典类型请求。
 *
 * @param typeName 展示名称
 * @param description 用途说明
 * @param enabled 是否启用
 * @param version Base64编码并发版本
 */
public record UpdateDictionaryTypeRequest(
        @NotBlank @Size(max = 100) String typeName,
        @NotBlank @Size(max = 500) String description,
        boolean enabled,
        @NotBlank String version
) {
}
