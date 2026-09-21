package cn.zqkj.platform.system.domain.model;

import java.time.LocalDateTime;

/**
 * 角色管理场景的基础只读投影，不包含权限集合。
 *
 * <p>对应表：{@code dbo.sys_role}（角色表）。</p>
 *
 * <p>业务说明：保存角色稳定代码、显示名称、启用状态和平台保护标记；
 * 权限集合由 {@code dbo.sys_role_permission}单独维护。</p>
 *
 * @param id 角色主键
 * @param roleCode 角色代码
 * @param roleName 角色名称
 * @param enabled 是否启用
 * @param systemManaged 是否由平台保护
 * @param createdAt 创建UTC时间
 * @param updatedAt 最后修改UTC时间
 * @param version SQL Server并发版本
 */
public record RoleSummary(
        long id,
        String roleCode,
        String roleName,
        boolean enabled,
        boolean systemManaged,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        byte[] version
) {

    /**
     * 创建角色摘要并复制可变的行版本数组。
     *
     * @param id 角色主键
     * @param roleCode 角色代码
     * @param roleName 角色名称
     * @param enabled 是否启用
     * @param systemManaged 是否由平台保护
     * @param createdAt 创建UTC时间
     * @param updatedAt 最后修改UTC时间
     * @param version SQL Server行版本
     */
    public RoleSummary {
        version = version == null ? null : version.clone();
    }

    /**
     * 返回行版本的防御性副本，避免调用方修改并发控制快照。
     *
     * @return SQL Server行版本副本
     */
    @Override
    public byte[] version() {
        return version == null ? null : version.clone();
    }
}
