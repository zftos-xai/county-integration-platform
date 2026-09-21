package cn.zqkj.platform.his.client;

import cn.zqkj.platform.his.exception.PhisRequestException;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 一次基层HIS协议调用使用的不可变运行上下文。
 *
 * <p>地址、超时和机构授权码必须作为一个整体传递。该对象包含敏感认证信息，访问方法
 * 仅对协议客户端包可见，字符串表示固定脱敏，禁止持久化、审计或序列化。</p>
 */
public final class PhisInvocationContext {

    /** 最小连接超时毫秒数。 */
    public static final int MIN_CONNECT_TIMEOUT_MS = 100;
    /** 最大连接超时毫秒数。 */
    public static final int MAX_CONNECT_TIMEOUT_MS = 60_000;
    /** 最大请求总超时毫秒数。 */
    public static final int MAX_REQUEST_TIMEOUT_MS = 300_000;

    private final URI soapEndpoint;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final String authorizationCode;

    /**
     * 使用已验证的非公开运行参数构建不可变上下文。
     *
     * @param soapEndpoint 实际SOAP提交地址
     * @param connectTimeoutMs 连接超时毫秒数
     * @param requestTimeoutMs 请求总超时毫秒数
     * @param authorizationCode 当前机构授权码
     */
    private PhisInvocationContext(
            URI soapEndpoint,
            int connectTimeoutMs,
            int requestTimeoutMs,
            String authorizationCode
    ) {
        this.soapEndpoint = soapEndpoint;
        this.connectTimeoutMs = connectTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
        this.authorizationCode = authorizationCode;
    }

    /**
     * 校验并创建一次调用上下文。
     *
     * <p>ASMX操作说明页允许携带固定的{@code op=PHIS_Interface}查询参数，但实际SOAP
     * 提交地址会移除该查询参数。</p>
     *
     * @param serviceUri 管理员保存的HTTP或HTTPS服务地址
     * @param connectTimeoutMs 连接超时毫秒，范围为100至60000
     * @param requestTimeoutMs 请求总超时毫秒，不得小于连接超时且最大为300000
     * @param authorizationCode 当前机构授权码
     * @return 已校验且固定脱敏的不可变调用上下文
     * @throws PhisRequestException 地址、超时或授权码不符合调用要求时抛出
     */
    public static PhisInvocationContext create(
            URI serviceUri,
            int connectTimeoutMs,
            int requestTimeoutMs,
            String authorizationCode
    ) {
        URI soapEndpoint = resolveSoapEndpoint(serviceUri);
        validateTimeouts(connectTimeoutMs, requestTimeoutMs);
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new PhisRequestException("必须提供基层HIS机构授权码");
        }
        return new PhisInvocationContext(
                soapEndpoint, connectTimeoutMs, requestTimeoutMs, authorizationCode);
    }

    /** 返回实际接收SOAP POST且不含操作说明参数的地址。 */
    URI soapEndpoint() {
        return soapEndpoint;
    }

    /** 返回连接超时毫秒数。 */
    int connectTimeoutMs() {
        return connectTimeoutMs;
    }

    /** 返回整个HTTP请求允许使用的最大毫秒数。 */
    int requestTimeoutMs() {
        return requestTimeoutMs;
    }

    /** 返回仅供本次请求注入业务参数的机构授权码。 */
    String authorizationCode() {
        return authorizationCode;
    }

    /**
     * 返回固定脱敏文字，避免日志、调试器或异常拼接泄露运行配置。
     *
     * @return 不含地址、超时和授权码的固定描述
     */
    @Override
    public String toString() {
        return "PhisInvocationContext[REDACTED]";
    }

    /** 校验地址并移除仅供ASMX操作说明页使用的固定查询参数。 */
    private static URI resolveSoapEndpoint(URI serviceUri) {
        if (serviceUri == null) {
            throw new PhisRequestException("必须提供基层HIS服务地址");
        }
        boolean supportedScheme = "http".equalsIgnoreCase(serviceUri.getScheme())
                || "https".equalsIgnoreCase(serviceUri.getScheme());
        boolean supportedOperationQuery = serviceUri.getRawQuery() == null
                || "op=PHIS_Interface".equals(serviceUri.getRawQuery());
        if (!supportedScheme || serviceUri.getHost() == null || serviceUri.getUserInfo() != null
                || !supportedOperationQuery || serviceUri.getFragment() != null) {
            throw new PhisRequestException(
                    "基层HIS服务地址必须使用HTTP或HTTPS，且不能包含账号、密码、非支持的查询参数或片段");
        }
        if (serviceUri.getRawQuery() == null) {
            return serviceUri.normalize();
        }
        try {
            return new URI(serviceUri.getScheme(), null, serviceUri.getHost(), serviceUri.getPort(),
                    serviceUri.getPath(), null, null).normalize();
        } catch (URISyntaxException exception) {
            throw new PhisRequestException("基层HIS服务地址无法转换为SOAP提交地址", exception);
        }
    }

    /** 校验连接超时和请求总超时的关系及范围。 */
    private static void validateTimeouts(int connectTimeoutMs, int requestTimeoutMs) {
        if (connectTimeoutMs < MIN_CONNECT_TIMEOUT_MS || connectTimeoutMs > MAX_CONNECT_TIMEOUT_MS) {
            throw new PhisRequestException("基层HIS连接超时配置超出允许范围");
        }
        if (requestTimeoutMs < connectTimeoutMs || requestTimeoutMs > MAX_REQUEST_TIMEOUT_MS) {
            throw new PhisRequestException("基层HIS请求总超时配置超出允许范围");
        }
    }
}
