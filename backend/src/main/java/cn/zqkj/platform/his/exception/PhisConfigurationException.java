package cn.zqkj.platform.his.exception;

/**
 * 表示当前机构缺少可用的基层HIS连接或凭证配置。
 */
public class PhisConfigurationException extends RuntimeException {

    /**
     * 创建不包含地址和凭证内容的配置异常。
     *
     * @param message 安全错误说明
     */
    public PhisConfigurationException(String message) {
        super(message);
    }

    /**
     * 创建不包含地址和凭证内容的配置异常。
     *
     * @param message 安全错误说明
     * @param cause 原始异常
     */
    public PhisConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
