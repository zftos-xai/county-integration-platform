package cn.zqkj.platform.system.domain.model;

import java.time.LocalDateTime;

/**
 * 用户管理场景的基础只读投影，不包含角色和机构范围集合。
 *
 * <p>数据来源：主表 {@code dbo.sys_user}（平台用户表），关联
 * {@code dbo.org_organization}（机构表）补充主机构代码。</p>
 *
 * <p>业务说明：用于管理端用户列表和详情组装；角色与机构范围由独立关系表读取。</p>
 *
 * @param id 用户主键
 * @param loginName 登录名
 * @param displayName 显示名称
 * @param primaryOrganizationId 主机构主键
 * @param organizationCode 主机构代码
 * @param enabled 是否启用
 * @param mustChangePassword 是否必须修改密码
 * @param createdAt 创建UTC时间
 * @param updatedAt 最后修改UTC时间
 * @param version SQL Server并发版本
 */
public record ManagedUserSummary(
        long id,
        String loginName,
        String displayName,
        long primaryOrganizationId,
        String organizationCode,
        boolean enabled,
        boolean mustChangePassword,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        byte[] version
) {

    /**
     * 创建用户摘要并复制可变的行版本数组。
     *
     * @param id 用户主键
     * @param loginName 登录名
     * @param displayName 显示名称
     * @param primaryOrganizationId 主机构主键
     * @param organizationCode 主机构代码
     * @param enabled 是否启用
     * @param mustChangePassword 是否必须修改密码
     * @param createdAt 创建UTC时间
     * @param updatedAt 最后修改UTC时间
     * @param version SQL Server行版本
     */
    public ManagedUserSummary {
        version = version == null ? null : version.clone();
    }

    /**
     * 返回行版本的防御性副本，避免调用方绕过乐观锁快照。
     *
     * @return SQL Server行版本副本
     */
    @Override
    public byte[] version() {
        return version == null ? null : version.clone();
    }
}
