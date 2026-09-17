package cn.zqkj.platform.system.domain.model;

import java.time.LocalDateTime;

/**
 * 平台系统字典项快照。
 *
 * @param id 字典项主键
 * @param dictionaryTypeId 所属字典类型主键
 * @param itemCode 类型内稳定项代码
 * @param itemLabel 展示文本
 * @param sortOrder 展示顺序
 * @param enabled 是否启用
 * @param createdAt 创建UTC时间
 * @param updatedAt 最后修改UTC时间
 * @param version SQL Server并发版本
 */
public record DictionaryItem(
        long id,
        long dictionaryTypeId,
        String itemCode,
        String itemLabel,
        int sortOrder,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        byte[] version
) {
}
