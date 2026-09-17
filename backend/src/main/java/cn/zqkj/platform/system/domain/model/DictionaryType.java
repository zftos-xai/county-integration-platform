package cn.zqkj.platform.system.domain.model;

import java.time.LocalDateTime;

/**
 * 平台系统字典类型快照，不承载医疗业务目录。
 *
 * @param id 字典类型主键
 * @param typeCode 稳定类型代码
 * @param typeName 展示名称
 * @param description 用途说明
 * @param enabled 是否启用
 * @param createdAt 创建UTC时间
 * @param updatedAt 最后修改UTC时间
 * @param version SQL Server并发版本
 */
public record DictionaryType(
        long id,
        String typeCode,
        String typeName,
        String description,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        byte[] version
) {
}
