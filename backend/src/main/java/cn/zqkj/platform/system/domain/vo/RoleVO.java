package cn.zqkj.platform.system.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 表示平台管理角色及其功能权限快照。
 *
 * @param id 角色主键
 * @param roleCode 角色代码
 * @param roleName 角色名称
 * @param enabled 是否启用
 * @param systemManaged 是否由平台保护
 * @param createdAt 创建UTC时间
 * @param updatedAt 最后修改UTC时间
 * @param version SQL Server并发版本
 * @param permissionCodes 已授予权限代码
 */
public record RoleVO(
        long id,
        String roleCode,
        String roleName,
        boolean enabled,
        boolean systemManaged,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        byte[] version,
        List<String> permissionCodes
) {

    /**
     * 防止调用方持有可变集合或并发版本数组。
     *
     * @param id 角色主键
     * @param roleCode 角色代码
     * @param roleName 角色名称
     * @param enabled 是否启用
     * @param systemManaged 是否由平台保护
     * @param createdAt 创建UTC时间
     * @param updatedAt 最后修改UTC时间
     * @param version SQL Server并发版本
     * @param permissionCodes 已授予权限代码
     */
    public RoleVO {
        version = version == null ? null : version.clone();
        permissionCodes = List.copyOf(permissionCodes);
    }

    /**
     * 返回SQL Server行版本的防御性副本。
     *
     * @return SQL Server并发版本副本
     */
    @Override
    public byte[] version() {
        return version == null ? null : version.clone();
    }
}
