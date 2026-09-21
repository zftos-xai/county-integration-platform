package cn.zqkj.platform.system.configuration.domain.model;

/**
 * 表示机构接口配置通过100-008确认来源机构的结果。
 *
 * <p>只有 {@link #VERIFIED} 的基层HIS配置可以参与基础数据同步；未知结果不能被当作失败后直接重试。</p>
 */
public enum ExternalEndpointVerificationStatus {

    /** 配置已保存但尚未调用100-008。 */
    NOT_VERIFIED,
    /** 100-008返回了唯一的来源机构，接口可以参与后续同步。 */
    VERIFIED,
    /** HIS明确返回失败或未返回唯一机构，接口不可用。 */
    FAILED,
    /** 网络、超时或协议异常，未能确认HIS是否完成处理。 */
    RESULT_UNKNOWN
}
