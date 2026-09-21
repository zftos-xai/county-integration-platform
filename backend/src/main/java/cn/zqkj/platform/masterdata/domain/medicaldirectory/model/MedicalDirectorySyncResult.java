package cn.zqkj.platform.masterdata.domain.medicaldirectory.model;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;

/**
 * 保存100-004和100-005单一医疗目录类型在一轮同步中的可追溯数量事实。
 *
 * <p>数据来源：{@code dbo.md_medical_directory_sync_result}（医疗目录同步分项结果表）。</p>
 *
 * <p>业务说明：每个同步批次、每种医疗目录类型只保存一条最终结论，用于核对100-005声明数量、
 * 100-004实际返回数量和当前目录更新事实。</p>
 *
 * @param directoryType 中药、西药、诊疗或耗材目录类型
 * @param status 当前类型可确认的处理结论
 * @param declaredCount 100-005返回的同范围声明数量；无法确认时为空
 * @param returnedCount 100-004分页实际返回记录数
 * @param duplicateCount 批次内完全重复记录数
 * @param invalidCount 字段校验不通过记录数
 * @param conflictCount 同一来源编码返回冲突事实数
 * @param createdCount 新增当前目录记录数
 * @param updatedCount 更新或恢复有效记录数
 * @param unchangedCount 与当前记录一致的记录数
 * @param sourceMissingCount 完整范围未返回并标记无效记录数
 * @param activeCount 本类型处理后当前有效记录数；未完成时为空
 * @param failureSummary 受控失败摘要；完成时为空
 */
public record MedicalDirectorySyncResult(
        MedicalDirectoryType directoryType,
        MedicalDirectorySyncResultStatus status,
        Long declaredCount,
        long returnedCount,
        long duplicateCount,
        long invalidCount,
        long conflictCount,
        long createdCount,
        long updatedCount,
        long unchangedCount,
        long sourceMissingCount,
        Long activeCount,
        String failureSummary
) {
}
