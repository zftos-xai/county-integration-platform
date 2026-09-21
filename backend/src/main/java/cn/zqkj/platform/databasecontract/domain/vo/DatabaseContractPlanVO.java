package cn.zqkj.platform.databasecontract.domain.vo;

import cn.zqkj.platform.databasecontract.domain.model.DatabaseContractPlanStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据库契约维护方案完整输出。
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
 * @param version Base64行版本
 * @param items 差异明细
 */
public record DatabaseContractPlanVO(
        long id, String planNo, DatabaseContractPlanStatus status, int issueCount,
        int executableCount, String summary, String createdBy, LocalDateTime createdAt,
        String approvedBy, LocalDateTime approvedAt, String approvalNote, String executedBy,
        LocalDateTime executedAt, LocalDateTime verifiedAt, String failureMessage,
        LocalDateTime updatedAt, String version, List<DatabaseContractPlanItemVO> items) {
}
