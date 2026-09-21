package cn.zqkj.platform.system.configuration.service.impl;

import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointAuthentication;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointCredential;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证机构接口认证信息只以绑定服务地址的AES-GCM密文保存。
 */
class ExternalEndpointCredentialCipherTest {

    /** 验证加密后可按原服务地址恢复且密文不包含明文。 */
    @Test
    void encryptsAndDecryptsAuthenticationForExactEndpoint() {
        ExternalEndpointCredentialCipher cipher = new ExternalEndpointCredentialCipher(
                new ObjectMapper(), new SecureRandom(), "0123456789abcdef0123456789abcdef".getBytes()
        );
        ExternalEndpointAuthentication authentication = new ExternalEndpointAuthentication(
                "V01", "operator", "password", "AUTH-008"
        );

        var encrypted = cipher.encrypt(8L, authentication);
        var stored = new ExternalEndpointCredential(
                8L, encrypted.encryptedPayload(), encrypted.initializationVector(), encrypted.encryptionVersion()
        );

        assertEquals(authentication, cipher.decrypt(stored));
        assertNotEquals("AUTH-008", new String(encrypted.encryptedPayload()));
    }

    /** 验证密文不能复制给另一条机构连接使用。 */
    @Test
    void rejectsCiphertextMovedToAnotherEndpoint() {
        ExternalEndpointCredentialCipher cipher = new ExternalEndpointCredentialCipher(
                new ObjectMapper(), new SecureRandom(), "0123456789abcdef0123456789abcdef".getBytes()
        );
        var encrypted = cipher.encrypt(8L, new ExternalEndpointAuthentication(
                "V01", null, null, "AUTH-008"
        ));
        var moved = new ExternalEndpointCredential(
                9L, encrypted.encryptedPayload(), encrypted.initializationVector(), encrypted.encryptionVersion()
        );

        assertThrows(ResourceConflictException.class, () -> cipher.decrypt(moved));
    }
}
