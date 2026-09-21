package cn.zqkj.platform.system.configuration.domain.model;

import java.time.LocalDateTime;

/**
 * 平台系统字典项快照。
 *
 * <p>对应表：{@code dbo.sys_dictionary_item}（系统字典项表），通过
 * {@code dictionary_type_id}归属 {@code dbo.sys_dictionary_type}。</p>
 *
 * <p>业务说明：保存类型内稳定代码、展示文本、顺序和启用状态；被业务外键引用后
 * 不允许直接删除。</p>
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
