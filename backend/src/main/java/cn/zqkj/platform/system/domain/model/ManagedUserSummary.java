package cn.zqkj.platform.system.domain.model;

import java.time.LocalDateTime;

/**
 * 表示不含角色和范围集合的用户持久化快照。
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

    /** 防止外部持有可变并发版本数组。 */
    public ManagedUserSummary {
        version = version == null ? null : version.clone();
    }

    /** @return SQL Server并发版本副本 */
    @Override
    public byte[] version() {
        return version == null ? null : version.clone();
    }
}
