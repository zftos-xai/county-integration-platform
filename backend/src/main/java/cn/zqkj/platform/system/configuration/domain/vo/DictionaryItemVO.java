package cn.zqkj.platform.system.configuration.domain.vo;

import java.time.LocalDateTime;

/**
 * 平台系统字典项输出。
 *
 * @param id 字典项主键
 * @param dictionaryTypeId 所属类型主键
 * @param itemCode 稳定项代码
 * @param itemLabel 展示文本
 * @param sortOrder 展示顺序
 * @param enabled 是否启用
 * @param createdAt 创建UTC时间
 * @param updatedAt 最后修改UTC时间
 * @param version Base64编码并发版本
 */
public record DictionaryItemVO(
        long id,
        long dictionaryTypeId,
        String itemCode,
        String itemLabel,
        int sortOrder,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String version
) {
}
