package cn.zqkj.platform.framework.security.filter;

import cn.zqkj.platform.common.exception.ApiError;
import cn.zqkj.platform.system.configuration.domain.model.ExternalSystemCaller;
import cn.zqkj.platform.system.configuration.service.ExternalSystemInboundKeyAuthenticator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 仅对平台集成入口使用外部系统ID/Key识别调用方，不读取管理端Cookie会话。
 */
public class ExternalSystemApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalSystemApiKeyAuthenticationFilter.class);
    private static final String SYSTEM_ID_HEADER = "X-External-System-Id";
    private static final String SYSTEM_KEY_HEADER = "X-External-System-Key";

    private final ExternalSystemInboundKeyAuthenticator authenticator;
    private final ObjectMapper objectMapper;

    /**
     * 创建外部系统入站认证过滤器。
     *
     * @param authenticator 外部系统ID/Key校验服务
     * @param objectMapper 统一错误响应序列化器
     */
    public ExternalSystemApiKeyAuthenticationFilter(
            ExternalSystemInboundKeyAuthenticator authenticator,
            ObjectMapper objectMapper
    ) {
        this.authenticator = authenticator;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验请求头并在当前安全上下文中设置调用方身份。
     *
     * @param request 当前集成API请求
     * @param response 当前HTTP响应
     * @param filterChain 后续集成入口安全过滤器
     * @throws ServletException 安全过滤链处理失败时抛出
     * @throws IOException 请求或响应IO失败时抛出
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String systemCode = request.getHeader(SYSTEM_ID_HEADER);
        String key = request.getHeader(SYSTEM_KEY_HEADER);
        ExternalSystemCaller caller;
        try {
            caller = authenticator.authenticate(systemCode, key).orElse(null);
        } catch (RuntimeException exception) {
            LOGGER.error("External system authentication dependency unavailable: {}",
                    exception.getClass().getSimpleName());
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTHENTICATION_SERVICE_UNAVAILABLE", "平台外部系统认证暂不可用");
            return;
        }
        if (caller == null) {
            writeError(response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "外部系统认证失败");
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(caller, null, List.of()));
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }

    /**
     * 返回不缓存且不包含认证材料的统一错误。
     *
     * @param response 当前HTTP响应
     * @param status HTTP状态
     * @param code 稳定错误代码
     * @param message 非敏感错误说明
     * @throws IOException 错误响应写入失败时抛出
     */
    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        String requestId = MDC.get("requestId");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Request-Id", requestId);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                code, message, requestId, OffsetDateTime.now()
        ));
    }
}
