package cn.zqkj.platform.system.configuration.domain.dto;

import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 删除一个指定适用范围参数配置的请求。
 *
 * @param environment 部署环境代码
 * @param organizationId 可选机构主键
 * @param expectedVersion 八字节行版本，JSON字段version使用Base64
 */
public record DeleteParameterCommand(
        @NotNull ParameterEnvironment environment,
        @Positive Long organizationId,
        @JsonProperty("version")
        @NotNull @Size(min = 8, max = 8) byte[] expectedVersion
) {
}
