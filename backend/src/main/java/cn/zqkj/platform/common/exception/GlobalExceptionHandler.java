package cn.zqkj.platform.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import cn.zqkj.platform.his.exception.PhisConfigurationException;
import cn.zqkj.platform.his.exception.PhisRequestException;
import cn.zqkj.platform.integration.lis.domain.vo.LisIntegrationErrorVO;
import cn.zqkj.platform.integration.lis.exception.LisIntegrationOperationException;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 将控制器异常转换为稳定且不泄露内部信息的 API 错误响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 返回携带平台操作编号的LIS入口业务错误。
     *
     * @param exception 已认证LIS业务失败及其追踪编号
     * @param request 当前HTTP请求
     * @return 包含错误代码和操作编号的LIS错误响应
     */
    @ExceptionHandler(LisIntegrationOperationException.class)
    ResponseEntity<LisIntegrationErrorVO> handleLisIntegrationOperation(
            LisIntegrationOperationException exception,
            HttpServletRequest request
    ) {
        LOGGER.warn("LIS integration operation failed: {}", exception.errorCode());
        return ResponseEntity.status(exception.status()).body(new LisIntegrationErrorVO(
                exception.errorCode(), exception.getMessage(), requestId(request), exception.operationId(),
                exception.exchangeRequestId(), OffsetDateTime.now()));
    }

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
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            ServletRequestBindingException.class,
            InvalidRequestException.class,
            PhisRequestException.class
    })
    ResponseEntity<ApiError> handleValidation(Exception exception, HttpServletRequest request) {
        LOGGER.warn("Request validation failed");
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求参数不符合接口要求", request);
    }

    /**
     * 处理目标HIS端点或认证配置不可用。
     *
     * @param exception 基层HIS配置异常
     * @param request 当前HTTP请求
     * @return HTTP 409配置冲突响应
     */
    @ExceptionHandler(PhisConfigurationException.class)
    ResponseEntity<ApiError> handlePhisConfiguration(
            PhisConfigurationException exception,
            HttpServletRequest request
    ) {
        LOGGER.warn("HIS endpoint configuration unavailable");
        return response(HttpStatus.CONFLICT, "HIS_ENDPOINT_UNAVAILABLE",
                "目标基层HIS接口当前不可用", request);
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
     * 处理账号库不可用等认证依赖故障，避免将其误报为密码错误或写入登录失败审计。
     *
     * @param exception 原始认证依赖异常
     * @param request 当前 HTTP 请求
     * @return HTTP 503 错误响应
     */
    @ExceptionHandler(InternalAuthenticationServiceException.class)
    ResponseEntity<ApiError> handleAuthenticationServiceUnavailable(
            InternalAuthenticationServiceException exception,
            HttpServletRequest request
    ) {
        LOGGER.error("Platform authentication service unavailable");
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AUTHENTICATION_SERVICE_UNAVAILABLE",
                "登录服务暂不可用，请稍后重试",
                request
        );
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
     * 处理没有映射到控制器或静态资源的请求路径。
     *
     * @param exception Spring MVC未找到请求处理器时抛出的异常
     * @param request 当前 HTTP 请求
     * @return HTTP 404错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleUnknownRoute(NoResourceFoundException exception, HttpServletRequest request) {
        LOGGER.warn("Requested route was not found: {}", request.getRequestURI());
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
        return ResponseEntity.status(status)
                .body(new ApiError(code, message, requestId(request), OffsetDateTime.now()));
    }

    /** 取得当前请求链路编号，供集成入口错误和通用错误共同定位。 */
    private String requestId(HttpServletRequest request) {
        String requestId = MDC.get("requestId");
        if (requestId == null || requestId.isBlank()) {
            requestId = request.getHeader("X-Request-Id");
        }
        return requestId;
    }
}
