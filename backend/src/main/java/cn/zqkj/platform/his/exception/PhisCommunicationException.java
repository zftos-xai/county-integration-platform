package cn.zqkj.platform.his.exception;

/**
 * 表示调用基层HIS时发生HTTP状态、超时、中断或网络通信错误。
 *
 * <p>异常消息只包含可诊断的通信类别，不包含服务地址、SOAP正文或凭证。</p>
 */
public class PhisCommunicationException extends RuntimeException {

    /**
     * 创建通信异常。
     *
     * @param message 不含地址、正文和凭证的错误说明
     */
    public PhisCommunicationException(String message) {
        super(message);
    }

    /**
     * 创建包含底层原因的通信异常。
     *
     * @param message 不含地址、正文和凭证的错误说明
     * @param cause 底层通信错误
     */
    public PhisCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
