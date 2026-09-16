package cn.zqkj.platform.foundation.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 为每个 HTTP 请求建立可验证的请求编号并写入日志上下文。
 *
 * <p>不可信或超长的外部请求编号会被替换，避免日志注入和追踪字段失控。</p>
 */
@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final int MAX_REQUEST_ID_LENGTH = 64;

    /**
     * 校验或生成请求编号，在过滤链执行期间写入 MDC 和响应头，并在结束后清理。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param filterChain 后续过滤器链
     * @throws ServletException 后续请求处理发生 Servlet 错误时抛出
     * @throws IOException 读取请求或写入响应失败时抛出
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null
                || requestId.isBlank()
                || requestId.length() > MAX_REQUEST_ID_LENGTH
                || !requestId.matches("[A-Za-z0-9._-]+")) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }
}
