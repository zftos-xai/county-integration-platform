package cn.zqkj.platform.exchange.domain.model;

/**
 * 表示目标系统调用的最终交换结果。
 *
 * <p>区分业务成功、明确失败、无响应和已收到但不可解析的响应；不表达发送过程状态。</p>
 */
public enum ExchangeResult {

    /** 目标系统返回已确认的业务成功结果。 */
    SUCCESS,

    /** 目标系统明确返回业务失败，或请求发出前发生明确错误。 */
    FAILURE,

    /** 平台未取得目标系统业务响应。 */
    NO_RESPONSE,

    /** 已收到目标响应，但报文不符合协议，业务结论无法确认。 */
    INVALID_RESPONSE
}
