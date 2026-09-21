package cn.zqkj.platform.system.domain.model;

import java.time.LocalDateTime;

/**
 * 外部系统在指定环境和机构下的服务端点投影。
 *
 * <p>数据来源：主表 {@code dbo.sys_external_endpoint}（外部系统服务地址表），关联
 * {@code dbo.org_organization}（机构表）补充机构代码，并根据
 * {@code dbo.sys_external_endpoint_credential}（外部连接认证信息表）判断凭证是否已配置。</p>
 *
 * <p>业务说明：端点只有在启用、凭证可用且来源机构校验通过后，才能参与正式业务调用。</p>
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
 * @param organizationQueryName 调用100-008时使用的平台机构名称
 * @param sourceOrganizationId 100-008唯一返回的来源机构标识；未验证时为空
 * @param sourceOrganizationName 100-008返回的来源机构名称；未验证时为空
 * @param verificationStatus 来源机构自动校验状态
 * @param verifiedAt 最近一次成功校验的UTC时间；未成功时为空
 * @param verificationFailureSummary 不含地址、凭证和报文的校验失败摘要
 */
public record ExternalEndpoint(long id, long externalSystemId, ParameterEnvironment environment,
                               Long organizationId, String organizationCode, String baseUrl,
                               int connectTimeoutMs, int readTimeoutMs, String credentialReference,
                               boolean credentialConfigured, boolean enabled, LocalDateTime createdAt,
                               LocalDateTime updatedAt, byte[] version,
                               String organizationQueryName, String sourceOrganizationId,
                               String sourceOrganizationName,
                               ExternalEndpointVerificationStatus verificationStatus,
                               LocalDateTime verifiedAt, String verificationFailureSummary) {

    /**
     * 保持既有配置读取调用的构造兼容；旧记录均视为尚未通过自动校验。
     *
     * @param id 端点主键
     * @param externalSystemId 外部系统主键
     * @param environment 部署环境
     * @param organizationId 平台机构主键
     * @param organizationCode 平台机构编码
     * @param baseUrl 服务地址
     * @param connectTimeoutMs 连接超时毫秒
     * @param readTimeoutMs 读取超时毫秒
     * @param credentialReference 凭证引用
     * @param credentialConfigured 是否已配置凭证
     * @param enabled 是否启用
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     * @param version 并发版本
     */
    public ExternalEndpoint(long id, long externalSystemId, ParameterEnvironment environment,
                            Long organizationId, String organizationCode, String baseUrl,
                            int connectTimeoutMs, int readTimeoutMs, String credentialReference,
                            boolean credentialConfigured, boolean enabled, LocalDateTime createdAt,
                            LocalDateTime updatedAt, byte[] version) {
        this(id, externalSystemId, environment, organizationId, organizationCode, baseUrl,
                connectTimeoutMs, readTimeoutMs, credentialReference, credentialConfigured, enabled,
                createdAt, updatedAt, version, null, null, null,
                ExternalEndpointVerificationStatus.NOT_VERIFIED, null, null);
    }
}
