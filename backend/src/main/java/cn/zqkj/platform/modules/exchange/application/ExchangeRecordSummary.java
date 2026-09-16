package cn.zqkj.platform.modules.exchange.application;

import java.time.LocalDateTime;

/**
 * 交换记录查询用例返回的只读摘要。
 *
 * @param id 平台交换记录主键
 * @param requestId 跨组件追踪的请求编号
 * @param interfaceCode 接口登记代码
 * @param organizationCode 平台统一机构代码
 * @param sourceRecordId 源系统业务记录标识
 * @param callerSystemCode 实际调用方系统标识
 * @param targetSystemCode 实际目标系统标识
 * @param result 最终交换结果；旧记录尚未迁移时可为空
 * @param targetResultCode 目标系统返回码；未取得响应时可为空
 * @param resultMessage 不包含医疗正文的必要结果说明
 * @param durationMs 调用耗时毫秒数；旧记录或尚未完成时可为空
 * @param requestSummary 已脱敏的请求摘要；不得包含完整医疗正文
 * @param communicationErrorSummary 不包含地址和凭证的通信异常摘要
 * @param receivedAt 平台接收时间
 * @param processedAt 平台完成处理时间；尚未完成时为空
 */
public record ExchangeRecordSummary(
        long id,
        String requestId,
        String interfaceCode,
        String organizationCode,
        String sourceRecordId,
        String callerSystemCode,
        String targetSystemCode,
        ExchangeResult result,
        String targetResultCode,
        String resultMessage,
        Long durationMs,
        String requestSummary,
        String communicationErrorSummary,
        LocalDateTime receivedAt,
        LocalDateTime processedAt
) {
}
