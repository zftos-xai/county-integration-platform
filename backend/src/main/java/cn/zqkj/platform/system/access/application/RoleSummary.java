package cn.zqkj.platform.system.access.application;

import java.time.LocalDateTime;

/**
 * 表示不含权限集合的角色持久化快照。
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

    /** 防止外部持有可变并发版本数组。 */
    public RoleSummary {
        version = version == null ? null : version.clone();
    }

    /** @return SQL Server并发版本副本 */
    @Override
    public byte[] version() {
        return version == null ? null : version.clone();
    }
}
