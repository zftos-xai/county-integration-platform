package cn.zqkj.platform.system.domain.model;

/**
 * 外部系统端点的托管认证密文。
 *
 * <p>对应表：{@code dbo.sys_external_endpoint_credential}（外部连接认证信息表），
 * 与 {@code dbo.sys_external_endpoint}按端点主键一对一关联。</p>
 *
 * <p>业务说明：仅保存AES-GCM密文、随机向量和格式版本；解密后的认证信息
 * 只能在一次外部调用期间使用，不得回显或记录。</p>
 *
 * @param endpointId 服务地址主键
 * @param encryptedPayload AES-GCM密文及认证标签
 * @param initializationVector AES-GCM随机向量
 * @param encryptionVersion 加密格式版本
 */
public record ExternalEndpointCredential(
        long endpointId,
        byte[] encryptedPayload,
        byte[] initializationVector,
        int encryptionVersion
) {
}
