package cn.zqkj.platform.system.domain.model;

import java.time.LocalDateTime;

/**
 * 外部系统环境与适用机构服务地址快照。
 *
 * @param id 主键
 * @param externalSystemId 外部系统主键
 * @param environment 环境
 * @param organizationId 可选机构主键
 * @param organizationCode 可选机构代码
 * @param baseUrl HTTP或HTTPS基础地址
 * @param connectTimeoutMs 连接超时毫秒
 * @param readTimeoutMs 读取超时毫秒
 * @param credentialReference 认证信息内部定位符；只在服务层内部使用
 * @param credentialConfigured 是否存在可用的托管或兼容凭证
 * @param enabled 是否启用
 * @param createdAt 创建UTC时间
 * @param updatedAt 修改UTC时间
 * @param version SQL Server并发版本
 */
public record ExternalEndpoint(long id, long externalSystemId, ParameterEnvironment environment,
                               Long organizationId, String organizationCode, String baseUrl,
                               int connectTimeoutMs, int readTimeoutMs, String credentialReference,
                               boolean credentialConfigured, boolean enabled, LocalDateTime createdAt,
                               LocalDateTime updatedAt, byte[] version) {
}
