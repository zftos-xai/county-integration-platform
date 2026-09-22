package cn.zqkj.platform.system.configuration.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改平台系统字典项输入。
 *
 * <p>写入目标：{@code dbo.sys_dictionary_item}。业务说明：维护字典项展示信息和启用状态；字典类型和稳定代码不变，此输入不是完整表行。</p>
 *
 * @param itemLabel 展示文本
 * @param sortOrder 展示顺序
 * @param enabled 是否启用
 * @param expectedVersion 客户端上次读取的八字节行版本；JSON使用Base64字段version
 */
public record UpdateDictionaryItemCommand(
        @NotBlank @Size(max = 200) String itemLabel,
        @Min(0) @Max(999999) int sortOrder,
        @NotNull Boolean enabled,
        @JsonProperty("version")
        @NotNull @Size(min = 8, max = 8) byte[] expectedVersion
) {
    /**
     * 统一业务标识和名称的首尾空白。
     *
     * @param itemLabel 展示文本
     * @param sortOrder 展示顺序
     * @param enabled 是否启用
     * @param expectedVersion 客户端上次读取的八字节行版本；JSON使用Base64字段version
     */
    public UpdateDictionaryItemCommand {
        itemLabel = Func.trimToNull(itemLabel);
        expectedVersion = expectedVersion == null ? null : expectedVersion.clone();
    }

    /** 返回本次修改所依据的行版本副本。
     * @return 八字节并发版本
     */
    @Override
    public byte[] expectedVersion() {
        return expectedVersion == null ? null : expectedVersion.clone();
    }
}
