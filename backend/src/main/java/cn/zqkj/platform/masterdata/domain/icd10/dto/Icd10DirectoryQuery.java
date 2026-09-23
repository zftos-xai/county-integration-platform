package cn.zqkj.platform.masterdata.domain.icd10.dto;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10DiagnosisCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 平台公共ICD10目录的有界分页查询条件。
 * @param diagnosisCategory 可选西医或中医类别
 * @param keyword 可选名称、编码或助记码关键词
 * @param page 从1开始页码
 * @param pageSize 每页条数
 */
public record Icd10DirectoryQuery(
        Icd10DiagnosisCategory diagnosisCategory,
        @Size(max = 50) String keyword,
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer pageSize
) {
    /**
     * 统一空白筛选和默认分页值。
     *
     * @param diagnosisCategory 可选西医或中医类别
     * @param keyword 可选名称、编码或助记码关键词
     * @param page 从1开始的页码
     * @param pageSize 每页条数
     */
    public Icd10DirectoryQuery {
        keyword = Func.trimToNull(keyword);
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }

    /** @return SQL Server分页所需的从零开始偏移量。 */
    public long offset() { return (page - 1L) * pageSize; }
}
