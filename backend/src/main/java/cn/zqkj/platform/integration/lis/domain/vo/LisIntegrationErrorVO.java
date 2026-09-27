package cn.zqkj.platform.integration.lis.domain.vo;

import java.time.OffsetDateTime;

/**
 * 返回已认证LIS调用失败及其可供平台定位的编号。
 *
 * @param code 稳定的接口错误代码
 * @param message 不包含认证材料、地址或业务正文的错误说明
 * @param requestId HTTP请求链路编号
 * @param operationId 平台业务操作编号
 * @param exchangeRequestId 基层HIS调用编号；调用未发起时为空
 * @param timestamp 错误响应生成时间
 */
public record LisIntegrationErrorVO(
        String code,
        String message,
        String requestId,
        String operationId,
        String exchangeRequestId,
        OffsetDateTime timestamp
) {
}
