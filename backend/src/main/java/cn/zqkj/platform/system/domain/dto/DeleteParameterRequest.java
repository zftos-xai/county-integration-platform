package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 删除一个指定适用范围参数配置的请求。
 *
 * @param environment 部署环境代码
 * @param organizationId 可选机构主键
 * @param version Base64编码并发版本
 */
public record DeleteParameterRequest(
        @NotBlank @Size(max = 16) String environment,
        Long organizationId,
        @NotBlank String version
) {
}
