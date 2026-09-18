package cn.zqkj.platform.system.service.impl;

import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.system.domain.model.ExternalEndpointAuthentication;
import cn.zqkj.platform.system.domain.model.EncryptedExternalEndpointCredential;
import cn.zqkj.platform.system.domain.model.ExternalEndpointCredential;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * 使用应用主密钥加解密机构外部连接认证信息。
 *
 * <p>密文通过服务地址主键绑定，不能复制到另一条连接使用。任何异常都不得包含明文或密钥。</p>
 */
@Component
public class ExternalEndpointCredentialCipher {

    private static final int ENCRYPTION_VERSION = 1;
    private static final int INITIALIZATION_VECTOR_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;
    private final byte[] encryptionKey;

    /**
     * 创建认证信息加密器。
     *
     * @param objectMapper JSON解析器
     * @param configuredSecret 部署环境提供的独立加密密钥
     */
    @Autowired
    public ExternalEndpointCredentialCipher(
            ObjectMapper objectMapper,
            @Value("${platform.security.credential-encryption-key:}") String configuredSecret
    ) {
        this(objectMapper, new SecureRandom(), deriveKey(configuredSecret));
    }

    /**
     * 创建可测试的认证信息加密器。
     *
     * @param objectMapper JSON解析器
     * @param secureRandom 随机源
     * @param encryptionKey 256位AES密钥
     */
    ExternalEndpointCredentialCipher(ObjectMapper objectMapper, SecureRandom secureRandom, byte[] encryptionKey) {
        this.objectMapper = objectMapper;
        this.secureRandom = secureRandom;
        this.encryptionKey = encryptionKey.clone();
    }

    /**
     * 加密一组认证信息。
     *
     * @param endpointId 服务地址主键
     * @param authentication 明文认证信息
     * @return 可安全持久化的密文
     */
    public EncryptedExternalEndpointCredential encrypt(
            long endpointId,
            ExternalEndpointAuthentication authentication
    ) {
        requireConfiguredKey();
        byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
        secureRandom.nextBytes(initializationVector);
        try {
            byte[] plaintext = objectMapper.writeValueAsBytes(authentication);
            byte[] encryptedPayload = cipher(Cipher.ENCRYPT_MODE, endpointId, initializationVector).doFinal(plaintext);
            java.util.Arrays.fill(plaintext, (byte) 0);
            return new EncryptedExternalEndpointCredential(
                    encryptedPayload,
                    initializationVector,
                    ENCRYPTION_VERSION
            );
        } catch (IOException | GeneralSecurityException exception) {
            throw new ResourceConflictException("接口认证信息暂时无法安全保存，请联系平台管理员");
        }
    }

    /**
     * 解密一组认证信息。
     *
     * @param credential 数据库密文
     * @return 仅供本次调用使用的明文认证信息
     */
    public ExternalEndpointAuthentication decrypt(ExternalEndpointCredential credential) {
        requireConfiguredKey();
        if (credential.encryptionVersion() != ENCRYPTION_VERSION) {
            throw new ResourceConflictException("接口认证信息的加密版本不受支持");
        }
        try {
            byte[] plaintext = cipher(
                    Cipher.DECRYPT_MODE,
                    credential.endpointId(),
                    credential.initializationVector()
            ).doFinal(credential.encryptedPayload());
            try {
                return objectMapper.readValue(plaintext, ExternalEndpointAuthentication.class);
            } finally {
                java.util.Arrays.fill(plaintext, (byte) 0);
            }
        } catch (AEADBadTagException exception) {
            throw new ResourceConflictException("接口认证信息校验失败，请重新配置");
        } catch (IOException | GeneralSecurityException exception) {
            throw new ResourceConflictException("接口认证信息暂时无法读取，请联系平台管理员");
        }
    }

    /** @param mode 加密或解密模式 @param endpointId 服务地址主键 @param initializationVector 随机向量 @return 已初始化密码器 */
    private Cipher cipher(int mode, long endpointId, byte[] initializationVector) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(encryptionKey, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH_BITS, initializationVector));
        cipher.updateAAD(("external-endpoint:" + endpointId).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    /** 拒绝在部署密钥缺失时处理认证信息。 */
    private void requireConfiguredKey() {
        if (encryptionKey.length == 0) {
            throw new ResourceConflictException("平台尚未配置认证信息加密密钥，请联系平台管理员");
        }
    }

    /** @param configuredSecret 部署密钥文本 @return 经过域隔离派生的256位AES密钥 */
    private static byte[] deriveKey(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            return new byte[0];
        }
        if (configuredSecret.length() < 32) {
            throw new IllegalStateException("PLATFORM_CREDENTIAL_ENCRYPTION_KEY must contain at least 32 characters");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(("external-endpoint-credential-v1:" + configuredSecret)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize credential encryption", exception);
        }
    }
}
