package cn.zqkj.platform.system.configuration.domain.dto;

/**
 * 创建平台系统字典类型命令。
 *
 * @param typeCode 稳定类型代码
 * @param typeName 展示名称
 * @param description 用途说明
 */
public record CreateDictionaryTypeCommand(String typeCode, String typeName, String description) {
}
