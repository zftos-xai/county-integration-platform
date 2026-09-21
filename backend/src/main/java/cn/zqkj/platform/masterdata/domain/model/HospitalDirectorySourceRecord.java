package cn.zqkj.platform.masterdata.domain.model;

import cn.zqkj.platform.his.domain.model.HospitalDirectoryType;

/**
 * 一条通过自动字段校验、准备直接对账的100-003来源目录记录。
 *
 * <p>该类型只存在于本次内存处理和短事务参数中，不表示暂存表或待人工发布数据。</p>
 *
 * <p>持久化目标：通过 {@code HospitalDirectorySyncMapper}对账写入
 * {@code dbo.md_hospital_directory}（医院综合目录正式数据表）；本类不是完整表行快照。</p>
 *
 * @param directoryType 来源目录类型
 * @param sourceCode 来源稳定编码
 * @param sourceName 来源名称
 * @param mnemonicCode 可选助记码
 * @param categoryName 可选类别名称
 * @param remark 可选非敏感备注
 * @param departmentCode 可选关联科室编码
 * @param departmentName 可选关联科室名称
 * @param wardName 可选关联病区编码
 * @param sourceOrganizationCode 来源返回机构编码
 */
public record HospitalDirectorySourceRecord(
        HospitalDirectoryType directoryType,
        String sourceCode,
        String sourceName,
        String mnemonicCode,
        String categoryName,
        String remark,
        String departmentCode,
        String departmentName,
        String wardName,
        String sourceOrganizationCode
) {
}
