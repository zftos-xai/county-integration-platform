package cn.zqkj.platform.masterdata.domain.model;

import cn.zqkj.platform.his.domain.model.HospitalDirectoryType;

/**
 * 按医院综合目录类型汇总的当前有效记录数。
 *
 * <p>数据来源：{@code dbo.md_hospital_directory}（医院综合目录正式数据表），
 * 关联 {@code dbo.org_organization}执行机构范围过滤后分组统计。</p>
 *
 * <p>业务说明：该类是聚合投影，不对应单独数据表。</p>
 *
 * @param directoryType 医院综合目录类型
 * @param total 当前正式记录数
 */
public record HospitalDirectoryTypeCount(HospitalDirectoryType directoryType, long total) {
}
