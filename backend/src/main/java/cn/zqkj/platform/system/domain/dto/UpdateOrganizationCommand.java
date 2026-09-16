package cn.zqkj.platform.system.domain.dto;

import java.time.LocalDateTime;

/**
 * 承载修改平台机构的已解析输入。
 *
 * @param organizationName 机构名称
 * @param organizationType 经确认的机构类型代码
 * @param parentId 可选父机构主键
 * @param validFrom 可选有效起始UTC时间
 * @param validTo 可选有效结束UTC时间
 * @param expectedVersion 客户端上次读取的并发版本
 */
public record UpdateOrganizationCommand(
        String organizationName,
        String organizationType,
        Long parentId,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        byte[] expectedVersion
) {
}
