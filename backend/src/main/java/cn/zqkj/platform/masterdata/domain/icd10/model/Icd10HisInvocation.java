package cn.zqkj.platform.masterdata.domain.icd10.model;

import cn.zqkj.platform.exchange.domain.model.ExchangeResult;
import java.time.LocalDateTime;

/**
 * 一次公共ICD10来源调用完成后的脱敏持久化事实。
 *
 * <p>持久化目标：{@code dbo.md_icd10_his_invocation}（ICD10同步HIS调用记录表）。
 * 业务说明：一行对应100-006或100-007的一次真实调用终态，只保存分页条件和受控摘要，
 * 不保存SOAP完整报文、端点地址、认证信息或患者正文。</p>
 *
 * @param batchId 所属公共ICD10同步批次主键
 * @param diagnosisCategory 本次调用的西医或中医类别
 * @param invocationSequence 批次类别内从一开始的调用顺序
 * @param tradeCode 已确认的HIS交易代码
 * @param pageStart 100-006起始行，100-007为空
 * @param pageEnd 100-006结束行，100-007为空
 * @param requestSummary 不含凭证的请求参数摘要
 * @param outcomeStatus 最终通信或业务结果
 * @param resultCode 可选HIS结果代码
 * @param responseSummary 不含原始报文的响应或错误摘要
 * @param returnedCount 可确认的返回或声明数量
 * @param durationMs 调用耗时毫秒数
 * @param requestedAt 开始调用UTC时间
 * @param completedAt 确认终态UTC时间
 */
public record Icd10HisInvocation(
        long batchId,
        Icd10DiagnosisCategory diagnosisCategory,
        int invocationSequence,
        String tradeCode,
        Long pageStart,
        Long pageEnd,
        String requestSummary,
        ExchangeResult outcomeStatus,
        String resultCode,
        String responseSummary,
        Long returnedCount,
        long durationMs,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {
}
