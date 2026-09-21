package cn.zqkj.platform.system.identity.domain.dto;

/**
 * 承载修改平台用户基础资料的已解析输入。
 *
 * @param displayName 显示名称
 * @param primaryOrganizationId 主机构主键
 * @param expectedVersion SQL Server并发版本
 */
public record UpdateUserCommand(String displayName, long primaryOrganizationId, byte[] expectedVersion) {

    /**
     * 防止外部持有可变并发版本数组。
     *
     * @param displayName 显示名称
     * @param primaryOrganizationId 主机构主键
     * @param expectedVersion SQL Server并发版本
     */
    public UpdateUserCommand {
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
