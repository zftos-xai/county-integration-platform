package cn.zqkj.platform.exchange.domain.model;

import java.time.LocalDateTime;

/**
 * 一次真实目标系统调用完成后的最小持久化事实。
 *
 * <p>持久化目标：{@code dbo.exch_exchange_record}（交换记录表）。该类是写入参数，
 * 不是数据库完整行的读取投影。</p>
 *
 * <p>业务说明：只接受不含敏感内容的摘要和业务记录引用，不承载完整XML、JSON、
 * 病历或报告正文；请求编号由数据库唯一约束防止重复落库。</p>
 *
 * @param requestId 跨组件定位本次调用的请求编号
 * @param interfaceCode 接口事件码或交易码
 * @param callerSystemCode 实际调用方系统标识
 * @param targetSystemCode 实际目标系统标识
 * @param organizationCode 平台统一机构代码
 * @param sourceRecordId 不包含患者正文的业务记录引用
 * @param result 最终交换结果
 * @param targetResultCode 目标系统返回码；无响应或请求发出前失败时为空
 * @param resultMessage 不包含医疗正文的必要结果说明
 * @param durationMs 调用耗时毫秒数
 * @param requestSummary 已脱敏的请求摘要
 * @param communicationErrorSummary 不包含地址、凭证和医疗正文的通信异常摘要
 * @param receivedAt 平台开始处理本次调用的 UTC 时间
 * @param processedAt 平台确认本次最终结果的 UTC 时间
 */
public record ExchangeRuntimeRecord(
        String requestId,
        String interfaceCode,
        String callerSystemCode,
        String targetSystemCode,
        String organizationCode,
        String sourceRecordId,
        ExchangeResult result,
        String targetResultCode,
        String resultMessage,
        long durationMs,
        String requestSummary,
        String communicationErrorSummary,
        LocalDateTime receivedAt,
        LocalDateTime processedAt
) {
}
