package cn.zqkj.platform.system.domain.vo;

import java.time.LocalDateTime;

/**
 * 外部系统服务端点的安全API输出，不回显任何认证秘密。
 *
 * @param id 主键
 * @param externalSystemId 系统主键
 * @param environment 环境
 * @param organizationId 可选机构
 * @param organizationCode 可选机构代码
 * @param baseUrl HTTP或HTTPS地址
 * @param connectTimeoutMs 连接超时
 * @param readTimeoutMs 读取超时
 * @param credentialConfigured 是否已配置接口认证信息
 * @param enabled 是否可参与运行
 * @param createdAt 创建UTC时间
 * @param updatedAt 修改UTC时间
 * @param version Base64并发版本
 * @param sourceOrganizationId 已确认HIS来源机构ID
 * @param sourceOrganizationName 已确认HIS来源机构名称
 * @param verificationStatus 自动校验结果
 * @param verifiedAt 自动校验完成时间
 * @param verificationFailureSummary 不包含凭证的失败说明
 */
public record ExternalEndpointVO(long id, long externalSystemId, String environment, Long organizationId,
                                 String organizationCode, String baseUrl, int connectTimeoutMs, int readTimeoutMs,
                                 boolean credentialConfigured, boolean enabled, LocalDateTime createdAt,
                                 LocalDateTime updatedAt, String version,
                                 String sourceOrganizationId, String sourceOrganizationName,
                                 String verificationStatus, LocalDateTime verifiedAt,
                                 String verificationFailureSummary) {
}
