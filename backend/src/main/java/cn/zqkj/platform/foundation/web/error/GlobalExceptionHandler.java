package cn.zqkj.platform.foundation.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

/**
 * 将控制器异常转换为稳定且不泄露内部信息的 API 错误响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理 Bean Validation 和控制器参数校验错误。
     *
     * @param exception 原始校验异常
     * @param request 当前 HTTP 请求
     * @return HTTP 400 错误响应
     */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            InvalidRequestException.class
    })
    ResponseEntity<ApiError> handleValidation(Exception exception, HttpServletRequest request) {
        LOGGER.warn("Request validation failed");
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求参数不符合接口要求", request);
    }

    /**
     * 处理机构范围或功能权限拒绝。
     *
     * @param exception 原始拒绝异常
     * @param request 当前 HTTP 请求
     * @return HTTP 403 错误响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        LOGGER.warn("Access denied");
        return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权执行该操作", request);
    }

    /**
     * 处理本地登录失败且不泄露账号是否存在或失败细节。
     *
     * @param exception 原始认证异常
     * @param request 当前HTTP请求
     * @return HTTP 401错误响应
     */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        LOGGER.warn("Platform authentication failed");
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "登录名或密码错误", request);
    }

    /**
     * 处理已被删除、不存在或对当前用例不可见的资源。
     *
     * @param exception 资源不存在异常
     * @param request 当前 HTTP 请求
     * @return HTTP 404 错误响应
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        LOGGER.warn("Requested resource was not found: {}", exception.getMessage());
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "请求的资源不存在", request);
    }

    /**
     * 处理唯一性、当前状态或并发版本冲突。
     *
     * @param exception 资源冲突异常
     * @param request 当前 HTTP 请求
     * @return HTTP 409 错误响应
     */
    @ExceptionHandler(ResourceConflictException.class)
    ResponseEntity<ApiError> handleConflict(ResourceConflictException exception, HttpServletRequest request) {
        LOGGER.warn("Resource state conflict: {}", exception.getMessage());
        return response(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "资源状态已变更，请刷新后重试", request);
    }

    /**
     * 处理没有专用映射的系统异常并保留受控服务端日志。
     *
     * @param exception 原始系统异常
     * @param request 当前 HTTP 请求
     * @return HTTP 500 错误响应
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected request processing failure", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "系统处理失败，请使用请求编号联系管理员", request);
    }

    /**
     * 构造带请求编号和生成时间的统一错误响应。
     *
     * @param status HTTP 状态
     * @param code 稳定业务错误代码
     * @param message 面向调用方的非敏感错误说明
     * @param request 当前 HTTP 请求
     * @return 指定状态和内容的响应实体
     */
    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        String requestId = MDC.get("requestId");
        if (requestId == null || requestId.isBlank()) {
            requestId = request.getHeader("X-Request-Id");
        }
        return ResponseEntity.status(status)
                .body(new ApiError(code, message, requestId, OffsetDateTime.now()));
    }
}
