package cn.zqkj.platform.masterdata.domain.vo;

import cn.zqkj.platform.his.domain.model.HospitalDirectoryType;
import cn.zqkj.platform.masterdata.domain.model.HospitalDirectorySyncResultStatus;

/**
 * 返回给管理端的100-003单目录类型运行事实。
 *
 * @param directoryType 科室、医生、病区或床位目录类型
 * @param status 当前类型的可确认处理结论
 * @param returnedCount HIS实际返回记录数
 * @param duplicateCount 批次内重复记录数
 * @param invalidCount 字段不合法记录数
 * @param conflictCount 同一来源编码内容冲突数
 * @param createdCount 新增当前目录记录数
 * @param updatedCount 更新或恢复有效记录数
 * @param unchangedCount 与当前记录一致的记录数
 * @param sourceMissingCount 完整返回中缺失并标记无效的记录数
 * @param activeCount 本类型处理后当前有效记录数；未完成时为空
 * @param failureSummary 受控失败摘要；完成时为空
 */
public record HospitalDirectorySyncResultVO(
        HospitalDirectoryType directoryType,
        HospitalDirectorySyncResultStatus status,
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
