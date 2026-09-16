package cn.zqkj.platform.foundation.web.error;

/**
 * 表示请求指定的平台资源不存在或对当前用例不可见。
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * 使用非敏感资源说明创建异常。
     *
     * @param message 供服务端日志和测试定位的资源说明
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
