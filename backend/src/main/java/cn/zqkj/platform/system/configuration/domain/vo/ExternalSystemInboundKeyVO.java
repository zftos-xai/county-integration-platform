package cn.zqkj.platform.system.configuration.domain.vo;

/**
 * 一次性展示新生成的县医院入站调用Key。
 *
 * @param systemCode 外部系统稳定代码
 * @param key 仅本次响应返回的明文Key
 * @param system 更新后的外部系统状态及并发版本
 */
public record ExternalSystemInboundKeyVO(String systemCode, String key, ExternalSystemVO system) {
}
