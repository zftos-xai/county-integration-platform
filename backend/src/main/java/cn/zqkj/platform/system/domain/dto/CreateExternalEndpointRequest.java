package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

/** @param environment 环境 @param organizationId 可选机构 @param baseUrl HTTP或HTTPS地址 @param connectTimeoutMs 连接超时 @param readTimeoutMs 读取超时 @param authentication 接口认证信息 @param enabled 保存后是否启用 */
public record CreateExternalEndpointRequest(@NotBlank String environment, Long organizationId,
                                            @NotBlank @Size(max = 500) String baseUrl,
                                            @Min(100) @Max(60000) int connectTimeoutMs,
                                            @Min(100) @Max(300000) int readTimeoutMs,
                                            @Valid @NotNull ExternalEndpointAuthenticationRequest authentication,
                                            boolean enabled) {
}
