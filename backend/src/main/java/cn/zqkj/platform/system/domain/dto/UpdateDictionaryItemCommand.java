package cn.zqkj.platform.system.domain.dto;

/**
 * 并发修改平台系统字典项命令。
 *
 * @param itemLabel 展示文本
 * @param sortOrder 展示顺序
 * @param enabled 是否启用
 * @param expectedVersion SQL Server并发版本
 */
public record UpdateDictionaryItemCommand(
        String itemLabel,
        int sortOrder,
        boolean enabled,
        byte[] expectedVersion
) {
}
