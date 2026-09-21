package cn.zqkj.platform.system.identity.domain.dto;

/**
 * 承载修改非系统角色的已解析输入。
 *
 * @param roleName 角色名称
 * @param enabled 是否启用
 * @param expectedVersion SQL Server并发版本
 */
public record UpdateRoleCommand(String roleName, boolean enabled, byte[] expectedVersion) {

    /**
     * 防止外部持有可变并发版本数组。
     *
     * @param roleName 角色名称
     * @param enabled 是否启用
     * @param expectedVersion SQL Server并发版本
     */
    public UpdateRoleCommand {
        expectedVersion = expectedVersion == null ? null : expectedVersion.clone();
    }

    /**
     * 返回并发版本的防御性副本。
     *
     * @return 并发版本副本
     */
    @Override
    public byte[] expectedVersion() {
        return expectedVersion == null ? null : expectedVersion.clone();
    }
}
