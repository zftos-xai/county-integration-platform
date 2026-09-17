package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建平台系统字典项请求。
 *
 * @param itemCode 类型内稳定项代码
 * @param itemLabel 展示文本
 * @param sortOrder 展示顺序
 */
public record CreateDictionaryItemRequest(
        @NotBlank @Size(max = 64) String itemCode,
        @NotBlank @Size(max = 200) String itemLabel,
        @Min(0) @Max(999999) int sortOrder
) {
}
