package cn.zqkj.platform.common.exception;

/**
 * 表示请求与当前资源状态、唯一性或并发版本冲突。
 */
public class ResourceConflictException extends RuntimeException {

    /**
     * 使用非敏感冲突说明创建异常。
     *
     * @param message 供服务端日志和测试定位的冲突说明
     */
    public ResourceConflictException(String message) {
        super(message);
    }
}
