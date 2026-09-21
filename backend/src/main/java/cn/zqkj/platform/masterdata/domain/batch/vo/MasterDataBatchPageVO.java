package cn.zqkj.platform.masterdata.domain.batch.vo;

import java.util.List;

/**
 * 返回真实分页的同步批次列表。
 *
 * @param items 当前页批次摘要
 * @param total 满足条件的批次总数
 * @param page 从1开始的当前页码
 * @param pageSize 当前页大小
 */
public record MasterDataBatchPageVO(
        List<MasterDataBatchSummaryVO> items,
        long total,
        int page,
        int pageSize
) {

    /**
     * 防止调用方在响应创建后修改当前页列表。
     *
     * @param items 当前页批次摘要
     * @param total 满足条件的批次总数
     * @param page 从1开始的当前页码
     * @param pageSize 当前页大小
     */
    public MasterDataBatchPageVO {
        items = List.copyOf(items);
    }
}
