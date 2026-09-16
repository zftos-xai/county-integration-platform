package cn.zqkj.platform.modules.exchange.application;

/**
 * 表示目标系统调用的最终交换结果。
 *
 * <p>该枚举只描述成功、明确失败和未取得业务响应三种事实，不表达发送过程状态。</p>
 */
public enum ExchangeResult {

    /** 目标系统返回已确认的业务成功结果。 */
    SUCCESS,

    /** 目标系统明确返回业务失败，或请求发出前发生明确错误。 */
    FAILURE,

    /** 平台未取得目标系统业务响应。 */
    NO_RESPONSE
}
