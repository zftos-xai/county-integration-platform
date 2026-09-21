package cn.zqkj.platform.masterdata.domain.medicaldirectory.model;

import java.util.List;
import java.util.Set;

/**
 * 保存一次完整来源范围与平台当前目录比对后的直接更新动作。
 *
 * @param recordsToUpsert 通过字段和编码校验、可新增或更新的来源记录
 * @param codesToInvalidate 已确认不再由HIS返回、应标记无效的当前目录编码
 */
public record MedicalDirectoryReconciliationResult(
        List<MedicalDirectorySourceRecord> recordsToUpsert,
        Set<String> codesToInvalidate
) {

    /**
     * 创建不可变的目录对账动作。
     *
     * @param recordsToUpsert 通过字段和编码校验、可新增或更新的来源记录
     * @param codesToInvalidate 已确认不再由HIS返回、应标记无效的当前目录编码
     */
    public MedicalDirectoryReconciliationResult {
        recordsToUpsert = List.copyOf(recordsToUpsert == null ? List.of() : recordsToUpsert);
        codesToInvalidate = Set.copyOf(codesToInvalidate == null ? Set.of() : codesToInvalidate);
    }
}
