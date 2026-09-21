package cn.zqkj.platform.system.organization.domain.vo;

import java.time.LocalDateTime;

/**
 * 平台机构档案的API只读投影。
 *
 * <p>数据来源：{@code dbo.org_organization}（机构表）。</p>
 *
 * <p>业务说明：用于展示机构层级、有效期和启停状态；行版本用于防止管理端覆盖他人修改。</p>
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
public record OrganizationVO(
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
     * 创建机构投影并复制可变的行版本数组。
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
     * @param version SQL Server行版本
     */
    public OrganizationVO {
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
