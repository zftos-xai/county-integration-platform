package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 平台参数值写入请求。
 *
 * @param environment 部署环境代码
 * @param organizationId 可选机构主键
 * @param value 参数值
 * @param enabled 是否启用
 * @param version Base64编码并发版本；首次创建时为空
 */
public record UpsertParameterRequest(
        @NotBlank @Size(max = 16) String environment,
        Long organizationId,
        @NotNull @Size(max = 1000) String value,
        boolean enabled,
        String version
) {
}
