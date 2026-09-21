package cn.zqkj.platform.system.configuration.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建平台系统字典类型请求。
 *
 * @param typeCode 稳定类型代码
 * @param typeName 展示名称
 * @param description 用途说明
 */
public record CreateDictionaryTypeRequest(
        @NotBlank @Size(max = 64) String typeCode,
        @NotBlank @Size(max = 100) String typeName,
        @NotBlank @Size(max = 500) String description
) {
}
