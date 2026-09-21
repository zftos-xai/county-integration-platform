package cn.zqkj.platform.system.domain.model;

/**
 * 业务域调用外部系统所需的完整运行配置。
 *
 * <p>配置域负责解析和解密认证信息，调用方不得持久化或记录该对象。</p>
 *
 * @param endpoint 已启用的机构接口配置
 * @param authentication 解密后的机构认证信息
 */
public record ExternalEndpointRuntimeConfiguration(
        ExternalEndpoint endpoint,
        ExternalEndpointAuthentication authentication
) {

    /**
     * 返回固定脱敏文字，防止地址和解密后的认证信息进入日志。
     *
     * @return 不包含端点与认证字段的固定描述
     */
    @Override
    public String toString() {
        return "ExternalEndpointRuntimeConfiguration[REDACTED]";
    }
}
