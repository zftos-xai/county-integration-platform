package cn.zqkj.platform.masterdata.domain.icd10.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 接收公共ICD10批次HIS调用事实的有界分页参数。
 *
 * <p>该对象不对应数据表；业务说明：只控制批次详情中已落库调用事实的读取范围，
 * 不会重新调用HIS或读取原始报文。</p>
 *
 * @param page 从一开始的页码，省略时为一
 * @param pageSize 每页记录数，省略时为50且最多100
 */
public record Icd10HisInvocationQuery(@Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {

    /**
     * 使用稳定默认值，避免详情页无界读取数百条调用事实。
     *
     * @param page 从一开始的页码；为空时采用一
     * @param pageSize 每页大小；为空时采用五十
     */
    public Icd10HisInvocationQuery {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 50 : pageSize;
    }

    /**
     * 转换为SQL Server 2012分页偏移量。
     *
     * @return 从零开始的稳定偏移量
     */
    public long offset() {
        return (long) (page - 1) * pageSize;
    }
}
