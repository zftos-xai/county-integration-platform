package cn.zqkj.platform.masterdata.domain.dto;

import cn.zqkj.platform.his.domain.model.HospitalDirectoryType;

/**
 * 医院综合目录的机构内有界分页查询条件。
 *
 * @param organizationCode 可选平台机构编码
 * @param directoryType 可选目录类型
 * @param keyword 可选目录编码或名称关键词
 * @param page 从1开始的页码
 * @param pageSize 页大小
 */
public record HospitalDirectoryQuery(
        String organizationCode,
        HospitalDirectoryType directoryType,
        String keyword,
        int page,
        int pageSize
) {
    /**
     * 计算SQL Server分页所需的从零开始偏移量。
     *
     * @return SQL Server分页偏移量
     */
    public int offset() {
        return (page - 1) * pageSize;
    }
}
