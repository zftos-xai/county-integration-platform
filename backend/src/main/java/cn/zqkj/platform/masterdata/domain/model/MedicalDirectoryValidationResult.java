package cn.zqkj.platform.masterdata.domain.model;

import java.util.List;

/**
 * 保存单类100-004目录的自动校验结果。
 *
 * @param declaredCount 100-005声明行数
 * @param returnedCount 分页实际取得行数
 * @param duplicateCount 完全相同的稳定编码重复行数
 * @param invalidCount 缺少必填字段或未确认启用值的行数
 * @param conflictCount 同一稳定编码返回互相冲突事实的行数
 * @param acceptedRecords 可直接幂等更新当前目录的唯一记录
 * @param failureSummary 受控失败摘要；通过时为空
 */
public record MedicalDirectoryValidationResult(
        long declaredCount,
        long returnedCount,
        long duplicateCount,
        long invalidCount,
        long conflictCount,
        List<MedicalDirectorySourceRecord> acceptedRecords,
        String failureSummary
) {

    /**
     * 判断全部来源记录是否通过自动校验。
     *
     * @return 本次取得的全部记录是否均通过自动校验
     */
    public boolean valid() {
        return failureSummary == null;
    }
}
