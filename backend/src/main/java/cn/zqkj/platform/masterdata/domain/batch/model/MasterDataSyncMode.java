package cn.zqkj.platform.masterdata.domain.batch.model;

/** 区分医疗目录的近20年重取、指定时间查询及无时间范围的其他目录。 */
public enum MasterDataSyncMode {
    /** 来源交易不使用时间范围，例如100-003医院综合目录。 */
    NOT_APPLICABLE,
    /** 使用操作人明确指定的起止时间查询医疗目录。 */
    TIME_RANGE,
    /** 从批次创建时刻向前20年查询，不代表HIS历史数据已被证明完整覆盖。 */
    FULL
}
