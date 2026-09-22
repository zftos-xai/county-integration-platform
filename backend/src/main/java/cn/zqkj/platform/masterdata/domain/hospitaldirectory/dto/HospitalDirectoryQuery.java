package cn.zqkj.platform.masterdata.domain.hospitaldirectory.dto;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

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
        @Size(max = 64) String organizationCode,
        HospitalDirectoryType directoryType,
        @Size(max = 50) String keyword,
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer pageSize
) {
    /**
     * 将空白筛选视为未指定，并为省略的分页参数采用第一页、每页二十条。
     *
     * @param organizationCode 可选机构编码，最长64字符；首尾空白被裁剪
     * @param directoryType 可选目录类型
     * @param keyword 可选关键词，最长50字符；空白值视为无筛选
     * @param page 从1开始的页码
     * @param pageSize 每页条数，取值为1至100
     */
    public HospitalDirectoryQuery {
        organizationCode = Func.trimToNull(organizationCode);
        keyword = Func.trimToNull(keyword);
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }

    /**
     * 计算SQL Server分页所需的从零开始偏移量。
     *
     * @return SQL Server分页偏移量
     */
    public long offset() {
        return (page - 1L) * pageSize;
    }
}
