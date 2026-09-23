package cn.zqkj.platform.masterdata.domain.icd10.vo;

import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10DiagnosisCategory;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10SyncResultStatus;

/**
 * 公共ICD10批次按诊断类别保存的运行事实。
 *
 * <p>数据来源：{@code dbo.md_icd10_sync_result}（ICD10分项同步结果表）。业务说明：该结果不包含机构归属，
 * 仅用于复核公共目录按类别的取得、校验和发布结论。</p>
 *
 * @param diagnosisCategory 本次单独请求的西医或中医诊断类别
 * @param status 可确认处理状态
 * @param declaredCount 100-007同条件声明数量
 * @param returnedCount 100-006实际取得数量
 * @param duplicateCount 批内完全重复数量
 * @param invalidCount 自动校验不通过数量
 * @param conflictCount 同类别编码冲突数量
 * @param createdCount 新增公共目录数量
 * @param updatedCount 更新公共目录数量
 * @param unchangedCount 与当前公共目录一致数量
 * @param activeCount 本类别当前有效目录数量；未完成时为空
 * @param failureSummary 未完成时的受控失败摘要
 */
public record Icd10SyncResultVO(
        Icd10DiagnosisCategory diagnosisCategory,
        Icd10SyncResultStatus status,
        Long declaredCount,
        long returnedCount,
        long duplicateCount,
        long invalidCount,
        long conflictCount,
        long createdCount,
        long updatedCount,
        long unchangedCount,
        Long activeCount,
        String failureSummary
) {
}
