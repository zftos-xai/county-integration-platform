package cn.zqkj.platform.system.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 表示用户管理边界可返回的平台用户快照。
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
 * @param roleIds 已授予角色主键
 * @param organizationScopeIds 已授予机构范围主键
 */
public record ManagedUserVO(
        long id,
        String loginName,
        String displayName,
        long primaryOrganizationId,
        String organizationCode,
        boolean enabled,
        boolean mustChangePassword,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        byte[] version,
        List<Long> roleIds,
        List<Long> organizationScopeIds
) {

    /**
     * 防止调用方持有可变集合或并发版本数组。
     */
    public ManagedUserVO {
        version = version == null ? null : version.clone();
        roleIds = List.copyOf(roleIds);
        organizationScopeIds = List.copyOf(organizationScopeIds);
    }

    /** @return SQL Server并发版本副本 */
    @Override
    public byte[] version() {
        return version == null ? null : version.clone();
    }
}
