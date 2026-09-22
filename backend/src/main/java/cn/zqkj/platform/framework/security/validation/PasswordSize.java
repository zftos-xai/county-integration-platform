package cn.zqkj.platform.framework.security.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 在输入边界限制新密码为至少9个字符且UTF-8编码不超过BCrypt的72字节上限。 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordSizeValidator.class)
public @interface PasswordSize {
    /** @return 密码长度不满足编码器约束时的提示 */
    String message() default "密码至少9个字符，且UTF-8编码不得超过72字节";

    /** @return 适用的校验分组 */
    Class<?>[] groups() default {};

    /** @return Bean Validation 附加元数据类型 */
    Class<? extends Payload>[] payload() default {};
}
