package cn.zqkj.platform.system.domain.vo;

import java.time.LocalDateTime;

/** @param id 主键 @param externalSystemId 系统主键 @param environment 环境 @param organizationId 可选机构 @param organizationCode 可选机构代码 @param baseUrl HTTPS地址 @param connectTimeoutMs 连接超时 @param readTimeoutMs 读取超时 @param credentialConfigured 是否已配置凭证引用 @param enabled 是否启用 @param createdAt 创建UTC时间 @param updatedAt 修改UTC时间 @param version Base64并发版本 */
public record ExternalEndpointVO(long id, long externalSystemId, String environment, Long organizationId,
                                 String organizationCode, String baseUrl, int connectTimeoutMs, int readTimeoutMs,
                                 boolean credentialConfigured, boolean enabled, LocalDateTime createdAt,
                                 LocalDateTime updatedAt, String version) {
}
