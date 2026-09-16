package cn.zqkj.platform.common.exception;

/**
 * 表示已通过协议解析、但组合规则不合法的客户端请求。
 *
 * <p>异常消息仅用于服务端受控日志，API 响应使用统一的非敏感参数错误说明。</p>
 */
public class InvalidRequestException extends RuntimeException {

    /**
     * 创建客户端请求错误。
     *
     * @param message 供服务端定位的非敏感错误原因
     */
    public InvalidRequestException(String message) {
        super(message);
    }
}
