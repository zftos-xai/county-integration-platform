package cn.zqkj.platform.framework.security;

import cn.zqkj.platform.system.identity.domain.dto.ChangePasswordRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证新密码的入口约束与 BCrypt UTF-8 字节上限一致。 */
class PasswordSizeTest {

    /** 中文与 ASCII 都按字节上限判断，合法边界可由真实编码器处理。 */
    @Test
    void rejectsPasswordsBeyondBcryptByteLimit() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String password : java.util.List.of("a".repeat(72), "中".repeat(24))) {
                assertTrue(validator.validate(new ChangePasswordRequest("old", password)).isEmpty());
                var encoder = new BCryptPasswordEncoder(4);
                assertTrue(encoder.matches(password, encoder.encode(password)));
            }
            for (String password : java.util.List.of("a".repeat(73), "中".repeat(25), "short", " ".repeat(9))) {
                assertFalse(validator.validate(new ChangePasswordRequest("old", password)).isEmpty());
            }
        }
    }
}
