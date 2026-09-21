package cn.zqkj.platform.masterdata.domain.vo;

import java.util.List;

/**
 * 汇总发起基础数据同步前可以选择的真实来源与可执行业务。
 *
 * @param sources 当前账号范围内可用的HIS来源
 * @param businesses 平台当前已实现闭环的同步业务
 */
public record MasterDataSyncOptionsVO(
        List<MasterDataSyncSourceVO> sources,
        List<MasterDataSyncBusinessVO> businesses
) {
    /**
     * 防止调用方持有可变选项集合。
     *
     * @param sources 当前账号范围内可用的HIS来源
     * @param businesses 平台当前已实现闭环的同步业务
     */
    public MasterDataSyncOptionsVO {
        sources = List.copyOf(sources);
        businesses = List.copyOf(businesses);
    }
}
