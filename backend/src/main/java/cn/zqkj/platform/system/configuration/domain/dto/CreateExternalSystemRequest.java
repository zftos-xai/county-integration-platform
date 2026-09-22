package cn.zqkj.platform.system.configuration.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * 创建外部系统登记信息的API输入。
 *
 * <p>写入目标：{@code dbo.sys_external_system}。业务说明：登记被集成系统的稳定身份；服务地址和认证信息分别管理，此输入不是完整表行。</p>
 *
 * @param systemCode 稳定代码
 * @param systemName 名称
 * @param description 用途说明
 */
public record CreateExternalSystemRequest(@NotBlank @Size(max = 64) @Pattern(regexp = "[A-Z][A-Z0-9_.-]{0,63}") String systemCode,
                                          @NotBlank @Size(max = 100) String systemName,
                                          @NotBlank @Size(max = 500) String description) {
    /**
     * 固定外部系统的展示文本和代码格式。
     *
     * @param systemCode 稳定代码
     * @param systemName 名称
     * @param description 用途说明
     */
    public CreateExternalSystemRequest {
        systemName = Func.trimToNull(systemName);
        description = Func.trimToNull(description);
        systemCode = Func.trimToNull(systemCode);
        if (systemCode != null) {
            systemCode = systemCode.toUpperCase(Locale.ROOT);
        }
    }
}
