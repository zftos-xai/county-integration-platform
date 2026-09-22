package cn.zqkj.platform.system.configuration.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 更新外部系统登记信息的API输入。
 *
 * <p>写入目标：{@code dbo.sys_external_system}。业务说明：维护外部系统展示信息和启用状态；稳定系统代码不变，此输入不是完整表行。</p>
 *
 * @param systemName 名称
 * @param description 用途说明
 * @param enabled 是否启用
 * @param expectedVersion 八字节行版本，JSON字段version使用Base64
 */
public record UpdateExternalSystemRequest(@NotBlank @Size(max = 100) String systemName,
                                          @NotBlank @Size(max = 500) String description,
                                          @NotNull Boolean enabled, @JsonProperty("version")
                                          @NotNull @Size(min = 8, max = 8) byte[] expectedVersion) {
    /**
     * 固定外部系统的展示文本和代码格式。
     *
     * @param systemName 名称
     * @param description 用途说明
     * @param enabled 是否启用
     * @param expectedVersion 八字节行版本，JSON字段version使用Base64
     */
    public UpdateExternalSystemRequest {
        systemName = Func.trimToNull(systemName);
        description = Func.trimToNull(description);
        expectedVersion = expectedVersion == null ? null : expectedVersion.clone();
    }
}
