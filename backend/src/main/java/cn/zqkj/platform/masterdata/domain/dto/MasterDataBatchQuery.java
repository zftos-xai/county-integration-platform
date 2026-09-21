package cn.zqkj.platform.masterdata.domain.dto;

import cn.zqkj.platform.masterdata.domain.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.model.MasterDataCategory;

import java.time.LocalDateTime;

/**
 * 保存同步批次正式列表的有界查询条件。
 *
 * @param organizationCode 可选平台机构代码
 * @param requestKey 可选请求标识，用于结果未知后精确回读
 * @param category 可选基础数据类别
 * @param status 可选批次状态
 * @param startedFrom 批次开始时间下界
 * @param startedTo 批次开始时间上界
 * @param page 从1开始的页码
 * @param pageSize 页大小
 */
public record MasterDataBatchQuery(
        String organizationCode,
        String requestKey,
        MasterDataCategory category,
        MasterDataBatchStatus status,
        LocalDateTime startedFrom,
        LocalDateTime startedTo,
        int page,
        int pageSize
) {

    /**
     * 计算SQL Server分页所需的从零开始偏移量。
     *
     * @return SQL Server分页使用的从零开始偏移量
     */
    public int offset() {
        return (page - 1) * pageSize;
    }
}
