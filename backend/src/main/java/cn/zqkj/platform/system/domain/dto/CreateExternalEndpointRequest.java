package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param environment 环境 @param organizationId 可选机构 @param baseUrl HTTPS地址 @param connectTimeoutMs 连接超时 @param readTimeoutMs 读取超时 @param credentialReference 凭证引用 */
public record CreateExternalEndpointRequest(@NotBlank String environment, Long organizationId,
                                            @NotBlank @Size(max = 500) String baseUrl,
                                            @Min(100) @Max(60000) int connectTimeoutMs,
                                            @Min(100) @Max(300000) int readTimeoutMs,
                                            @NotBlank @Size(max = 200) String credentialReference) {
}
