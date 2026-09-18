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
}
