package cn.zqkj.platform.system.configuration.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * 创建平台系统字典项输入。
 *
 * <p>写入目标：{@code dbo.sys_dictionary_item}。业务说明：为指定平台字典类型登记代码、展示文本和顺序；类型主键由调用路径提供，此输入不是完整表行。</p>
 *
 * @param itemCode 类型内稳定项代码
 * @param itemLabel 展示文本
 * @param sortOrder 展示顺序
 */
public record CreateDictionaryItemCommand(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Z][A-Z0-9_.-]{0,63}") String itemCode,
        @NotBlank @Size(max = 200) String itemLabel,
        @Min(0) @Max(999999) int sortOrder
) {
    /**
     * 统一业务标识和名称的首尾空白。
     *
     * @param itemCode 类型内稳定项代码
     * @param itemLabel 展示文本
     * @param sortOrder 展示顺序
     */
    public CreateDictionaryItemCommand {
        itemCode = Func.trimToNull(itemCode);
        if (itemCode != null) {
            itemCode = itemCode.toUpperCase(Locale.ROOT);
        }
        itemLabel = Func.trimToNull(itemLabel);
    }
}
