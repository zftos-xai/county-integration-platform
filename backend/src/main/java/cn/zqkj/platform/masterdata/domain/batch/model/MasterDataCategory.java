package cn.zqkj.platform.masterdata.domain.batch.model;

/**
 * 定义当前已形成完整闭环的基础数据同步业务。
 *
 * <p>后续基础数据业务在完成来源、校验和当前有效数据去向设计前，不得提前登记为可执行类别。</p>
 */
public enum MasterDataCategory {

    /** 一次完成100-003全部四类目录的医院综合目录同步。 */
    HOSPITAL_DIRECTORY("医院综合目录", "100-003", null, MasterDataScopeType.ORGANIZATION),
    /** 一次完成100-004、100-005四类医疗目录的数量核对和分页同步。 */
    MEDICAL_DIRECTORY("药品、诊疗和耗材目录", "100-004", "100-005", MasterDataScopeType.ORGANIZATION),
    /** 一次按西医和中医类别完成100-006、100-007数量核对的公共ICD10目录同步。 */
    ICD10_DIAGNOSIS("ICD10诊断目录", "100-006", "100-007", MasterDataScopeType.PLATFORM);

    private final String displayName;
    private final String dataTradeCode;
    private final String countTradeCode;
    private final MasterDataScopeType scopeType;

    /**
     * 创建基础数据类别定义。
     *
     * @param displayName 面向业务人员的名称
     * @param dataTradeCode 数据查询交易代码
     * @param countTradeCode 数量查询交易代码；来源接口不提供数量查询时为空
     * @param scopeType 业务归属范围
     */
    MasterDataCategory(
            String displayName,
            String dataTradeCode,
            String countTradeCode,
            MasterDataScopeType scopeType
    ) {
        this.displayName = displayName;
        this.dataTradeCode = dataTradeCode;
        this.countTradeCode = countTradeCode;
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
    public String dataTradeCode() {
        return dataTradeCode;
    }

    /**
     * 返回数量核对交易；来源不提供时返回空值。
     *
     * @return 数量查询交易；来源接口不提供时为空
     */
    public String countTradeCode() {
        return countTradeCode;
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
