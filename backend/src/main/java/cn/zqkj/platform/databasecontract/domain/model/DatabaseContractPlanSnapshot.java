package cn.zqkj.platform.databasecontract.domain.model;

import java.time.LocalDateTime;

/**
 * 数据库契约维护方案的只读快照。
 *
 * <p>对应表：{@code dbo.db_contract_plan}（数据库契约维护方案表）。</p>
 *
 * <p>业务说明：保存从契约差异生成到审批、执行、复验或取消的状态事实；
 * 行版本用于防止重复审批、执行或取消。</p>
 *
 * @param id 主键
 * @param planNo 方案编号
 * @param status 状态
 * @param issueCount 差异数
 * @param executableCount 可执行数
 * @param summary 摘要
 * @param createdBy 创建人
 * @param createdAt 创建时间
 * @param approvedBy 审批人
 * @param approvedAt 审批时间
 * @param approvalNote 审批说明
 * @param executedBy 执行人
 * @param executedAt 执行时间
 * @param verifiedAt 复验时间
 * @param failureMessage 失败摘要
 * @param updatedAt 更新时间
 * @param version SQL Server行版本
 */
public record DatabaseContractPlanSnapshot(
        long id, String planNo, DatabaseContractPlanStatus status, int issueCount,
        int executableCount, String summary, String createdBy, LocalDateTime createdAt,
        String approvedBy, LocalDateTime approvedAt, String approvalNote, String executedBy,
        LocalDateTime executedAt, LocalDateTime verifiedAt, String failureMessage,
        LocalDateTime updatedAt, byte[] version) {
}
