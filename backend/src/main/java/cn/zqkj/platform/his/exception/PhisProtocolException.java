package cn.zqkj.platform.his.exception;

/**
 * 表示基层HIS SOAP报文或业务响应不符合已确认协议。
 *
 * <p>异常只描述协议错误类别，不携带原始报文，避免验证码、账号和业务数据进入日志。</p>
 */
public class PhisProtocolException extends RuntimeException {

    /**
     * 创建协议异常。
     *
     * @param message 不含原始报文和凭证的错误说明
     */
    public PhisProtocolException(String message) {
        super(message);
    }

    /**
     * 创建包含底层原因的协议异常。
     *
     * @param message 不含原始报文和凭证的错误说明
     * @param cause XML或JSON解析失败原因
     */
    public PhisProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
