package cn.zqkj.platform.masterdata.domain.vo;

import cn.zqkj.platform.masterdata.domain.model.MasterDataCategory;

/**
 * 表示平台当前已经具备完整执行闭环的基础数据同步业务。
 *
 * @param category 稳定业务类别
 * @param name 业务名称
 * @param tradeCode HIS交易码
 */
public record MasterDataSyncBusinessVO(
        MasterDataCategory category,
        String name,
        String tradeCode
) {
}
