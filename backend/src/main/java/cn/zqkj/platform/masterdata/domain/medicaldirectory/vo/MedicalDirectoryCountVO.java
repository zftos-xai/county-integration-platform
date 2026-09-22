package cn.zqkj.platform.masterdata.domain.medicaldirectory.vo;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;

/**
 * 医疗目录按类型统计的API输出。
 *
 * <p>数据来源：{@code dbo.md_medical_directory}（医疗目录当前数据表），关联
 * {@code dbo.org_organization}执行机构范围过滤后按目录类型聚合。
 * 业务说明：该投影不对应单独数据表。</p>
 *
 * @param directoryType 医疗目录类型
 * @param total 当前有效记录数
 */
public record MedicalDirectoryCountVO(MedicalDirectoryType directoryType, long total) {
}
