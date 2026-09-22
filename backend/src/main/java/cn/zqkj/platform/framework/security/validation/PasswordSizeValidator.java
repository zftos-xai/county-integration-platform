package cn.zqkj.platform.framework.security.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/** 校验密码原值的字符和字节长度，不裁剪、不截断、不记录密码。 */
public final class PasswordSizeValidator implements ConstraintValidator<PasswordSize, String> {
    /** 缺失值交由NotBlank处理，非空值必须能够完整交给现有密码编码器。 */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || (value.length() >= 9 && value.getBytes(StandardCharsets.UTF_8).length <= 72);
    }
}
