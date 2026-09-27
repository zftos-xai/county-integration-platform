package cn.zqkj.platform.integration.lis.exception;

import org.springframework.http.HttpStatus;

/** 表示LIS平台入口在调用方已认证后遇到的可定位业务失败。 */
public class LisIntegrationOperationException extends RuntimeException {

    /** 当前失败对应的HTTP状态。 */
    private final HttpStatus status;
    /** 对接方可按该代码分支处理的稳定错误标识。 */
    private final String errorCode;
    /** 本次平台业务操作编号。 */
    private final String operationId;
    /** 已分配的基层HIS交换编号；HIS调用未开始时为空。 */
    private final String exchangeRequestId;

    private LisIntegrationOperationException(
            HttpStatus status,
            String errorCode,
            String message,
            String operationId,
            String exchangeRequestId
    ) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.operationId = operationId;
        this.exchangeRequestId = exchangeRequestId;
    }

    /**
     * 返回机构代码未找到或机构停用时的404业务错误。
     *
     * @param operationId 平台操作编号
     * @return 可追踪的机构路由错误
     */
    public static LisIntegrationOperationException notFound(String operationId) {
        return new LisIntegrationOperationException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND",
                "请求的基层机构不存在或未启用", operationId, null);
    }

    /**
     * 返回机构基层HIS端点尚不可用时的409业务错误。
     *
     * @param operationId 平台操作编号
     * @return 可追踪的基层HIS配置错误
     */
    public static LisIntegrationOperationException hisEndpointUnavailable(String operationId) {
        return new LisIntegrationOperationException(HttpStatus.CONFLICT, "HIS_ENDPOINT_UNAVAILABLE",
                "目标基层HIS接口当前不可用", operationId, null);
    }

    /**
     * 返回平台机构路由存储暂不可用时的503业务错误。
     *
     * @param operationId 平台操作编号
     * @return 可追踪的平台路由错误
     */
    public static LisIntegrationOperationException platformRoutingUnavailable(String operationId) {
        return new LisIntegrationOperationException(HttpStatus.SERVICE_UNAVAILABLE,
                "PLATFORM_ROUTING_UNAVAILABLE", "平台暂时无法查询目标基层机构，请稍后重试", operationId, null);
    }

    /**
     * 返回HIS调用已结束但交换事实未能持久化时的503业务错误。
     *
     * @param operationId 平台操作编号
     * @param exchangeRequestId 基层HIS交换编号
     * @return 可追踪的交换记录存储错误
     */
    public static LisIntegrationOperationException exchangeRecordUnavailable(
            String operationId,
            String exchangeRequestId
    ) {
        return new LisIntegrationOperationException(HttpStatus.SERVICE_UNAVAILABLE,
                "EXCHANGE_RECORD_UNAVAILABLE", "平台暂时无法保存本次交换记录，请按编号联系平台处理",
                operationId, exchangeRequestId);
    }

    /**
     * 返回该失败对应的HTTP状态。
     *
     * @return HTTP状态
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * 返回稳定的接口错误代码。
     *
     * @return 错误代码
     */
    public String errorCode() {
        return errorCode;
    }

    /**
     * 返回平台操作编号。
     *
     * @return 平台操作编号
     */
    public String operationId() {
        return operationId;
    }

    /**
     * 返回已分配的基层HIS交换编号；尚未调用HIS时为空。
     *
     * @return 基层HIS交换编号或空值
     */
    public String exchangeRequestId() {
        return exchangeRequestId;
    }
}
