package cn.zqkj.platform.system.configuration.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改平台系统字典类型输入。
 *
 * <p>写入目标：{@code dbo.sys_dictionary_type}。业务说明：维护系统字典分类名称、说明和启用状态；不改变类型代码，此输入不是完整表行。</p>
 *
 * @param typeName 展示名称
 * @param description 用途说明
 * @param enabled 是否启用
 * @param expectedVersion 客户端上次读取的八字节行版本；JSON使用Base64字段version
 */
public record UpdateDictionaryTypeCommand(
        @NotBlank @Size(max = 100) String typeName,
        @NotBlank @Size(max = 500) String description,
        @NotNull Boolean enabled,
        @JsonProperty("version")
        @NotNull @Size(min = 8, max = 8) byte[] expectedVersion
) {
    /**
     * 统一业务标识和名称的首尾空白。
     *
     * @param typeName 展示名称
     * @param description 用途说明
     * @param enabled 是否启用
     * @param expectedVersion 客户端上次读取的八字节行版本；JSON使用Base64字段version
     */
    public UpdateDictionaryTypeCommand {
        typeName = Func.trimToNull(typeName);
        description = Func.trimToNull(description);
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
