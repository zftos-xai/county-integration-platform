package cn.zqkj.platform.system.domain.model;

import java.time.LocalDateTime;

/**
 * 外部系统环境与机构作用域端点快照。
 *
 * @param id 主键
 * @param externalSystemId 外部系统主键
 * @param environment 环境
 * @param organizationId 可选机构主键
 * @param organizationCode 可选机构代码
 * @param baseUrl HTTPS基础地址
 * @param connectTimeoutMs 连接超时毫秒
 * @param readTimeoutMs 读取超时毫秒
 * @param credentialReference 凭证引用；只在服务层内部使用
 * @param enabled 是否启用
 * @param createdAt 创建UTC时间
 * @param updatedAt 修改UTC时间
 * @param version SQL Server并发版本
 */
public record ExternalEndpoint(long id, long externalSystemId, ParameterEnvironment environment,
                               Long organizationId, String organizationCode, String baseUrl,
                               int connectTimeoutMs, int readTimeoutMs, String credentialReference,
                               boolean enabled, LocalDateTime createdAt, LocalDateTime updatedAt, byte[] version) {
}
