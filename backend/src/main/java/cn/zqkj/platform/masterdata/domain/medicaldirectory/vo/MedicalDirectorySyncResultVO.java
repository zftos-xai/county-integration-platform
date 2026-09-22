package cn.zqkj.platform.masterdata.domain.medicaldirectory.vo;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySyncResultStatus;

/**
 * 输出100-004/100-005按医疗目录类型保存的同步处理事实。
 *
 * <p>数据来源及持久化目标：{@code dbo.md_medical_directory_sync_result}。
 * 业务说明：保存各医疗目录类型的数量核对和写入结果；本投影不含批次外键与技术时间列，
 * 不是完整表行，由写入和查询入口复用。</p>
 *
 * @param directoryType 中药、西药、诊疗或耗材目录类型
 * @param status 当前类型处理结论
 * @param declaredCount 100-005声明数量；结果未知时为空
 * @param returnedCount 100-004实际返回数量
 * @param duplicateCount 完全重复数量
 * @param invalidCount 自动校验不通过数量
 * @param conflictCount 同编码冲突数量
 * @param createdCount 新增当前数据数量
 * @param updatedCount 更新或恢复有效数量
 * @param unchangedCount 未变化数量
 * @param sourceMissingCount 来源缺失并失效数量
 * @param activeCount 处理后当前有效数量；未完成时为空
 * @param failureSummary 受控失败说明；完成时为空
 */
public record MedicalDirectorySyncResultVO(
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
