package cn.zqkj.platform.system.configuration.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 限制管理端登记的 HTTP/S 服务地址，禁止地址内凭证及未支持的查询参数。 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ServiceEndpointUrlValidator.class)
public @interface ServiceEndpointUrl {

    /**
     * 提供不回显原始地址和凭证的校验提示。
     * @return 安全的地址格式提示
     */
    String message() default "服务地址必须使用 HTTP 或 HTTPS；只允许 op=PHIS_Interface 查询参数，不得携带凭证或片段";

    /**
     * 指定启用本约束的校验组。
     * @return 默认使用 Bean Validation 默认组
     */
    Class<?>[] groups() default {};

    /**
     * 携带 Bean Validation 约束元数据。
     * @return 默认没有附加元数据
     */
    Class<? extends Payload>[] payload() default {};
}
