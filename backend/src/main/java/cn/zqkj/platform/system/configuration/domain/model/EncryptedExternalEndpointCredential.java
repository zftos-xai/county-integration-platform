package cn.zqkj.platform.system.configuration.domain.model;

/**
 * 等待持久化的外部连接认证密文。
 *
 * <p>持久化目标：{@code dbo.sys_external_endpoint_credential}，仅承载密文、随机向量与格式版本，
 * 不是完整表行。业务说明：供机构外部接口连接安全保存认证信息，不保存明文凭证。</p>
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
