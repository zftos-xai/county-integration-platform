package cn.zqkj.platform.masterdata.domain.medicaldirectory.model;

/**
 * 表示一次100-004/100-005运行中单一医疗目录类型的可确认处理结论。
 */
public enum MedicalDirectorySyncResultStatus {

    /** 数量核对、全页取得、自动校验和当前目录更新均已完成。 */
    COMPLETED,
    /** HIS明确拒绝请求或返回数据未通过自动校验，当前类型未更新。 */
    FAILED,
    /** 通信中断或超时，无法确认HIS是否完整处理，当前类型未更新。 */
    RESULT_UNKNOWN
}
