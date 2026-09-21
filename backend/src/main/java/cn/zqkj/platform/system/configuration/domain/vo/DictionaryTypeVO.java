package cn.zqkj.platform.system.configuration.domain.vo;

import java.time.LocalDateTime;

/**
 * 平台系统字典类型输出。
 *
 * @param id 字典类型主键
 * @param typeCode 稳定类型代码
 * @param typeName 展示名称
 * @param description 用途说明
 * @param enabled 是否启用
 * @param createdAt 创建UTC时间
 * @param updatedAt 最后修改UTC时间
 * @param version Base64编码并发版本
 */
public record DictionaryTypeVO(
        long id,
        String typeCode,
        String typeName,
        String description,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String version
) {
}
