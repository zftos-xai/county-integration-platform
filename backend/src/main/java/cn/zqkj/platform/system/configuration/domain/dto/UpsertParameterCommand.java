package cn.zqkj.platform.system.configuration.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 平台参数值写入请求。
 *
 * <p>写入目标：{@code dbo.sys_parameter_value}。业务说明：保存代码已登记参数在指定环境和机构的取值；新增或更新由当前记录及版本决定，此输入不是完整表行。</p>
 *
 * @param environment 部署环境代码
 * @param organizationId 可选机构主键
 * @param value 参数值
 * @param enabled 是否启用
 * @param expectedVersion 八字节行版本，JSON字段version使用Base64；首次创建时为空
 */
public record UpsertParameterCommand(
        @NotNull ParameterEnvironment environment,
        @Positive Long organizationId,
        @NotBlank @Size(max = 1000) String value,
        @NotNull Boolean enabled,
        @JsonProperty("version")
        @Size(min = 8, max = 8) byte[] expectedVersion
) {
    /**
     * 整理写入的参数文本，具体类型和取值范围由已登记的参数定义决定。
     *
     * @param environment 参数生效环境
     * @param organizationId 参数所属机构；全局参数为空
     * @param value 参数值，首尾空白不参与取值
     * @param enabled 是否启用该取值
     * @param expectedVersion 更新时的八字节行版本；创建时为空
     */
    public UpsertParameterCommand {
        value = Func.trimToNull(value);
    }
}
