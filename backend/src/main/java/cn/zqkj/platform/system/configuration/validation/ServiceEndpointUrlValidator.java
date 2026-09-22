package cn.zqkj.platform.system.configuration.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

/** 在请求校验阶段检查外部端点地址，不解析 DNS，也不发起网络访问。 */
public final class ServiceEndpointUrlValidator implements ConstraintValidator<ServiceEndpointUrl, String> {

    /**
     * 接受 HTTP/S 服务地址及已支持的 ASMX 操作页参数，拒绝内嵌凭证和片段。
     * @param value 已裁剪的端点地址；必填性由 NotBlank 约束处理
     * @param context Bean Validation 调用上下文
     * @return 地址结构符合登记规则时为 true
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        try {
            URI uri = new URI(value);
            boolean http = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            boolean supportedQuery = uri.getRawQuery() == null || "op=PHIS_Interface".equals(uri.getRawQuery());
            return http && uri.getHost() != null && uri.getUserInfo() == null
                    && supportedQuery && uri.getFragment() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
