package cn.zqkj.platform.system.configuration.domain.model;

/**
 * 已通过入站Key认证的外部系统身份。
 *
 * @param systemCode 外部系统稳定代码
 * @param systemName 外部系统名称
 */
public record ExternalSystemCaller(String systemCode, String systemName) {
}
