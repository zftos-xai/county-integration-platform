package cn.zqkj.platform.system.domain.dto;

/**
 * 服务层保存外部系统登记信息的规范化命令。
 *
 * @param systemCode 可选稳定代码
 * @param systemName 名称
 * @param description 用途说明
 * @param enabled 是否启用
 * @param expectedVersion 可选并发版本
 */
public record ExternalSystemCommand(String systemCode, String systemName, String description, boolean enabled,
                                    byte[] expectedVersion) {
}
