package cn.zqkj.platform.masterdata.domain.model;

import cn.zqkj.platform.his.domain.model.PhisTrade;

/**
 * 定义当前已形成完整闭环的基础数据同步业务。
 *
 * <p>后续基础数据业务在完成来源、校验和当前有效数据去向设计前，不得提前登记为可执行类别。</p>
 */
public enum MasterDataCategory {

    /** 一次完成100-003全部四类目录的医院综合目录同步。 */
    HOSPITAL_DIRECTORY("医院综合目录", PhisTrade.HOSPITAL_DIRECTORY_QUERY, null, MasterDataScopeType.ORGANIZATION);

    private final String displayName;
    private final PhisTrade dataTrade;
    private final PhisTrade countTrade;
    private final MasterDataScopeType scopeType;

    /**
     * 创建基础数据类别定义。
     *
     * @param displayName 面向业务人员的名称
     * @param dataTrade 数据查询交易
     * @param countTrade 数量查询交易；来源接口不提供数量查询时为空
     * @param scopeType 业务归属范围
     */
    MasterDataCategory(
            String displayName,
            PhisTrade dataTrade,
            PhisTrade countTrade,
            MasterDataScopeType scopeType
    ) {
        this.displayName = displayName;
        this.dataTrade = dataTrade;
        this.countTrade = countTrade;
        this.scopeType = scopeType;
    }

    /**
     * 返回面向业务人员的显示名称。
     *
     * @return 面向业务人员的类别名称
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 返回该基础数据类别使用的HIS数据交易。
     *
     * @return 取得数据使用的HIS交易
     */
    public PhisTrade dataTrade() {
        return dataTrade;
    }

    /**
     * 返回数量核对交易；来源不提供时返回空值。
     *
     * @return 数量查询交易；来源接口不提供时为空
     */
    public PhisTrade countTrade() {
        return countTrade;
    }

    /**
     * 返回该基础数据类别的业务归属范围。
     *
     * @return 该类别的业务归属范围
     */
    public MasterDataScopeType scopeType() {
        return scopeType;
    }
}
