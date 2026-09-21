package cn.zqkj.platform.system.organization.domain.dto;

import java.time.LocalDateTime;

/**
 * 承载创建平台机构的已解析输入。
 *
 * @param organizationCode 平台统一机构编码
 * @param organizationName 机构名称
 * @param organizationType 经确认的机构类型代码
 * @param parentId 可选父机构主键
 * @param validFrom 可选有效起始UTC时间
 * @param validTo 可选有效结束UTC时间
 */
public record CreateOrganizationCommand(
        String organizationCode,
        String organizationName,
        String organizationType,
        Long parentId,
        LocalDateTime validFrom,
        LocalDateTime validTo
) {
}
