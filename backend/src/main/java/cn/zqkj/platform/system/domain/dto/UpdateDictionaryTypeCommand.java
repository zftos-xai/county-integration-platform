package cn.zqkj.platform.system.domain.dto;

/**
 * 并发修改平台系统字典类型命令。
 *
 * @param typeName 展示名称
 * @param description 用途说明
 * @param enabled 是否启用
 * @param expectedVersion SQL Server并发版本
 */
public record UpdateDictionaryTypeCommand(
        String typeName,
        String description,
        boolean enabled,
        byte[] expectedVersion
) {
}
