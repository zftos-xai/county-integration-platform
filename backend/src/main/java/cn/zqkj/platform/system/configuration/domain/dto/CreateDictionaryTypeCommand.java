package cn.zqkj.platform.system.configuration.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * 创建平台系统字典类型输入。
 *
 * <p>写入目标：{@code dbo.sys_dictionary_type}。业务说明：登记平台系统字典的分类；不存储 HIS 业务目录，此输入不是完整表行。</p>
 *
 * @param typeCode 稳定类型代码
 * @param typeName 展示名称
 * @param description 用途说明
 */
public record CreateDictionaryTypeCommand(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Z][A-Z0-9_.-]{0,63}") String typeCode,
        @NotBlank @Size(max = 100) String typeName,
        @NotBlank @Size(max = 500) String description
) {
    /**
     * 统一业务标识和名称的首尾空白。
     *
     * @param typeCode 稳定类型代码
     * @param typeName 展示名称
     * @param description 用途说明
     */
    public CreateDictionaryTypeCommand {
        typeCode = Func.trimToNull(typeCode);
        if (typeCode != null) {
            typeCode = typeCode.toUpperCase(Locale.ROOT);
        }
        typeName = Func.trimToNull(typeName);
        description = Func.trimToNull(description);
    }
}
