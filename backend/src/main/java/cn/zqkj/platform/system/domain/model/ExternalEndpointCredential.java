package cn.zqkj.platform.system.domain.model;

/**
 * 数据库保存的外部连接认证密文。
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
