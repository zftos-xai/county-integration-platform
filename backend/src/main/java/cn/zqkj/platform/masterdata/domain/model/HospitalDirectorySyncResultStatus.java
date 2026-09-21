package cn.zqkj.platform.masterdata.domain.model;

/**
 * 表示一次100-003运行中单一目录类型的可确认处理结论。
 */
public enum HospitalDirectorySyncResultStatus {

    /** HIS完整返回且平台已完成自动校验和当前数据更新。 */
    COMPLETED,
    /** HIS明确拒绝请求或返回数据未通过自动校验，当前类型未更新。 */
    FAILED,
    /** 通信中断或超时，无法确认HIS是否完整处理，当前类型未更新。 */
    RESULT_UNKNOWN
}
