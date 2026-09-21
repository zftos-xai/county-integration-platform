package cn.zqkj.platform.his.exception;

/**
 * 表示基层HIS请求在发送前因调用上下文或业务参数无效而被拒绝。
 *
 * <p>捕获该异常时可以确定请求尚未发送，不得记录为“结果未知”。异常消息不得包含
 * 服务地址、认证信息或业务报文。</p>
 */
public class PhisRequestException extends RuntimeException {

    /**
     * 创建请求准备异常。
     *
     * @param message 不含地址、凭证和报文正文的错误说明
     */
    public PhisRequestException(String message) {
        super(message);
    }

    /**
     * 创建包含底层原因的请求准备异常。
     *
     * @param message 不含地址、凭证和报文正文的错误说明
     * @param cause 请求构造失败原因
     */
    public PhisRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
