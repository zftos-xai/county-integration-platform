package cn.zqkj.platform.system.configuration.domain.dto;

import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;

/**
 * 服务层保存外部系统端点的规范化命令。
 *
 * @param environment 可选环境
 * @param organizationId 可选机构
 * @param baseUrl HTTP或HTTPS地址
 * @param connectTimeoutMs 连接超时
 * @param readTimeoutMs 读取超时
 * @param authentication 可选接口认证信息
 * @param enabled 是否请求启用
 * @param expectedVersion 可选并发版本
 */
public record ExternalEndpointCommand(ParameterEnvironment environment, Long organizationId, String baseUrl,
                                      int connectTimeoutMs, int readTimeoutMs,
                                      ExternalEndpointAuthenticationCommand authentication,
                                      boolean enabled, byte[] expectedVersion) {

    /**
     * 兼容仍携带历史100-008查询名称的内部调用；机构名称只从平台机构档案读取。
     *
     * @param environment 环境
     * @param organizationId 机构主键
     * @param baseUrl 服务地址
     * @param connectTimeoutMs 连接超时
     * @param readTimeoutMs 读取超时
     * @param authentication 接入信息
     * @param enabled 请求启用状态
     * @param expectedVersion 并发版本
     * @param ignoredOrganizationQueryName 已废弃的历史查询名称，不参与配置或接口调用
     */
    public ExternalEndpointCommand(ParameterEnvironment environment, Long organizationId, String baseUrl,
                                   int connectTimeoutMs, int readTimeoutMs,
                                   ExternalEndpointAuthenticationCommand authentication,
                                   boolean enabled, byte[] expectedVersion,
                                   String ignoredOrganizationQueryName) {
        this(environment, organizationId, baseUrl, connectTimeoutMs, readTimeoutMs,
                authentication, enabled, expectedVersion);
    }
}
