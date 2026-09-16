package cn.zqkj.platform.modules.organization.application;

import java.time.LocalDateTime;

/**
 * 表示平台机构的可读快照。
 *
 * @param id 平台内部机构主键
 * @param organizationCode 平台统一机构编码
 * @param organizationName 机构名称
 * @param organizationType 经项目确认的机构类型代码
 * @param parentId 父机构主键；顶级机构为空
 * @param enabled 机构是否启用
 * @param validFrom 可选有效起始UTC时间
 * @param validTo 可选有效结束UTC时间
 * @param createdAt 档案创建UTC时间
 * @param updatedAt 档案最后修改UTC时间
 * @param version SQL Server并发版本
 */
public record OrganizationView(
        Long id,
        String organizationCode,
        String organizationName,
        String organizationType,
        Long parentId,
        boolean enabled,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        byte[] version
) {

    /**
     * 创建机构快照并防止外部持有可变并发版本数组。
     */
    public OrganizationView {
        version = version == null ? null : version.clone();
    }

    /**
     * 返回并发版本的防御性副本。
     *
     * @return SQL Server并发版本副本
     */
    @Override
    public byte[] version() {
        return version == null ? null : version.clone();
    }
}
