package cn.zqkj.platform.masterdata.domain.batch.vo;

import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;

/**
 * 表示平台当前已经具备完整执行闭环的基础数据同步业务。
 *
 * @param category 稳定业务类别
 * @param name 业务名称
 * @param tradeCode HIS数据交易码
 * @param countTradeCode HIS数量核对交易码；来源不支持时为空
 * @param requiresTimeRange 是否必须选择来源查询时间范围
 */
public record MasterDataSyncBusinessVO(
        MasterDataCategory category,
        String name,
        String tradeCode,
        String countTradeCode,
        boolean requiresTimeRange
) {
}
