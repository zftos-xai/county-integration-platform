package cn.zqkj.platform.system.configuration.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

/**
 * 更新外部系统服务端点的API输入。
 *
 * @param environment 环境
 * @param organizationId 可选机构
 * @param baseUrl HTTP或HTTPS地址
 * @param connectTimeoutMs 连接超时
 * @param readTimeoutMs 读取超时
 * @param authentication 可选新认证信息；空值表示保留现有配置
 * @param enabled 是否请求启用
 * @param version Base64并发版本
 */
public record UpdateExternalEndpointRequest(@NotBlank String environment,
                                            Long organizationId,
                                            @NotBlank @Size(max = 500) String baseUrl,
                                            @Min(100) @Max(60000) int connectTimeoutMs,
                                            @Min(100) @Max(300000) int readTimeoutMs,
                                            @Valid ExternalEndpointAuthenticationRequest authentication,
                                            boolean enabled,
                                            @NotBlank String version) {
}
