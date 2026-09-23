package cn.zqkj.platform.masterdata.domain.icd10.vo;

import cn.zqkj.platform.exchange.domain.model.ExchangeResult;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10DiagnosisCategory;
import java.time.LocalDateTime;

/**
 * 批次详情安全展示的一次公共ICD10 HIS调用事实。
 *
 * <p>数据来源：{@code dbo.md_icd10_his_invocation}（ICD10同步HIS调用记录表）。
 * 业务说明：返回受控请求和响应摘要供批次复核，不返回完整SOAP报文、端点地址或认证信息。</p>
 *
 * @param id 调用事实内部主键
 * @param diagnosisCategory 西医或中医类别
 * @param invocationSequence 类别内调用顺序
 * @param tradeCode HIS交易代码
 * @param pageStart 可选分页起始行
 * @param pageEnd 可选分页结束行
 * @param requestSummary 脱敏请求摘要
 * @param outcomeStatus 已确认调用终态
 * @param resultCode 可选HIS结果代码
 * @param responseSummary 脱敏响应或错误摘要
 * @param returnedCount 可确认的返回或声明数量
 * @param durationMs 调用耗时毫秒数
 * @param requestedAt 请求UTC时间
 * @param completedAt 完成UTC时间
 */
public record Icd10HisInvocationVO(
        long id,
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
