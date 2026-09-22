package cn.zqkj.platform.masterdata.domain.batch.model;

/** 区分医疗目录的主动全量查询、指定时间查询及不使用时间范围的其他目录。 */
public enum MasterDataSyncMode {
    /** 来源交易不使用时间范围，例如100-003医院综合目录。 */
    NOT_APPLICABLE,
    /** 使用操作人明确指定的起止时间查询医疗目录。 */
    TIME_RANGE,
    /** 使用服务端已确认的完整来源范围查询医疗目录。 */
    FULL
}
