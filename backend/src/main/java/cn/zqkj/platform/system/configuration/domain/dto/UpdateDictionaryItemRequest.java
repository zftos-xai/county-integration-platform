package cn.zqkj.platform.system.configuration.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改平台系统字典项请求。
 *
 * @param itemLabel 展示文本
 * @param sortOrder 展示顺序
 * @param enabled 是否启用
 * @param version Base64编码并发版本
 */
public record UpdateDictionaryItemRequest(
        @NotBlank @Size(max = 200) String itemLabel,
        @Min(0) @Max(999999) int sortOrder,
        boolean enabled,
        @NotBlank String version
) {
}
