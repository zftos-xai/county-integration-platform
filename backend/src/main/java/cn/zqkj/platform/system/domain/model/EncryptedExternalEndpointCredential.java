package cn.zqkj.platform.system.domain.model;

/**
 * 等待持久化的外部连接认证密文。
 *
 * @param encryptedPayload AES-GCM密文及认证标签
 * @param initializationVector AES-GCM随机向量
 * @param encryptionVersion 加密格式版本
 */
public record EncryptedExternalEndpointCredential(
        byte[] encryptedPayload,
        byte[] initializationVector,
        int encryptionVersion
) {
}
