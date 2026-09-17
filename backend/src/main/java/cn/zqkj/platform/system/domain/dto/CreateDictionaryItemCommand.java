package cn.zqkj.platform.system.domain.dto;

/**
 * 创建平台系统字典项命令。
 *
 * @param itemCode 类型内稳定项代码
 * @param itemLabel 展示文本
 * @param sortOrder 展示顺序
 */
public record CreateDictionaryItemCommand(String itemCode, String itemLabel, int sortOrder) {
}
