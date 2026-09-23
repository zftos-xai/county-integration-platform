package cn.zqkj.platform.masterdata.domain.icd10.model;

import java.util.List;

/**
 * 保存单一ICD10诊断类别的来源数量与质量校验结论。
 *
 * <p>该类型是外部取得与后续持久化之间的内存边界，不对应独立数据表，也不能替代真实来源联调对稳定唯一键的确认。</p>
 *
 * @param declaredCount 100-007同范围声明行数
 * @param returnedCount 100-006分页实际取得行数
 * @param duplicateCount 完全相同来源事实的重复行数
 * @param invalidCount 缺少必填字段或超过文档长度的行数
 * @param conflictCount 同一类别和来源稳定身份返回冲突事实的组数
 * @param acceptedRecords 可继续进入同步流程的唯一来源记录
 * @param failureSummary 受控失败摘要；校验通过时为空
 */
public record Icd10ValidationResult(
        long declaredCount,
        long returnedCount,
        long duplicateCount,
        long invalidCount,
        long conflictCount,
        List<Icd10SourceRecord> acceptedRecords,
        String failureSummary
) {

    /**
     * 判断当前类别的数量和来源事实是否全部通过自动校验。
     *
     * @return 无数量差异、无无效行、重复行或冲突行时为true
     */
    public boolean valid() {
        return failureSummary == null;
    }
}
