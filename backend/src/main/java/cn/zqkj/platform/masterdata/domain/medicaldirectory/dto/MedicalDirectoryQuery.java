package cn.zqkj.platform.masterdata.domain.medicaldirectory.dto;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 医疗目录的机构内有界分页查询条件。
 *
 * @param organizationCode 可选平台机构编码
 * @param directoryType 可选医疗目录类型
 * @param keyword 可选目录编码、名称或助记码关键词
 * @param sourceEnabledFlag 可选HIS启用原值，精确匹配，不代表平台有效状态
 * @param page 从1开始的页码
 * @param pageSize 页大小
 */
public record MedicalDirectoryQuery(
        @Size(max = 64) String organizationCode,
        MedicalDirectoryType directoryType,
        @Size(max = 50) String keyword,
        @Size(max = 100) String sourceEnabledFlag,
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer pageSize
) {
    /**
     * 将空白筛选视为未指定，并为省略的分页参数采用第一页、每页二十条。
     *
     * @param organizationCode 可选机构编码，最长64字符；首尾空白被裁剪
     * @param directoryType 可选目录类型
     * @param keyword 可选关键词，最长50字符；空白值视为无筛选
     * @param sourceEnabledFlag 可选HIS启用原值，最长100字符；空白值视为无筛选
     * @param page 从1开始的页码
     * @param pageSize 每页条数，取值为1至100
     */
    public MedicalDirectoryQuery {
        organizationCode = Func.trimToNull(organizationCode);
        keyword = Func.trimToNull(keyword);
        sourceEnabledFlag = Func.trimToNull(sourceEnabledFlag);
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
