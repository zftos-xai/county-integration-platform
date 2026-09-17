package cn.zqkj.platform.system.domain.dto;

import cn.zqkj.platform.system.domain.model.ParameterEnvironment;

/** @param environment 可选环境 @param organizationId 可选机构 @param baseUrl HTTPS地址 @param connectTimeoutMs 连接超时 @param readTimeoutMs 读取超时 @param credentialReference 凭证引用 @param enabled 是否启用 @param expectedVersion 可选并发版本 */
public record ExternalEndpointCommand(ParameterEnvironment environment, Long organizationId, String baseUrl,
                                      int connectTimeoutMs, int readTimeoutMs, String credentialReference,
                                      boolean enabled, byte[] expectedVersion) {
}
