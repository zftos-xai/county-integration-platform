package cn.zqkj.platform.system.configuration.domain.model;

/**
 * 外部系统调用平台时使用的内部认证快照。
 *
 * <p>业务说明：只用于服务端校验入站Key，不对外输出。</p>
 * <p>来源表：{@code dbo.sys_external_system}（外部系统表）。</p>
 *
 * @param systemCode 外部系统稳定代码
 * @param systemName 外部系统名称
 * @param enabled 外部系统是否启用
 * @param keyHash 入站Key的BCrypt单向校验值
 */
public record ExternalSystemInboundCredential(String systemCode, String systemName, boolean enabled, String keyHash) {
}
