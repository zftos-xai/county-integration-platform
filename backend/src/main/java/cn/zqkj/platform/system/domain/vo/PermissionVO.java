package cn.zqkj.platform.system.domain.vo;

/**
 * 后端代码注册、可供角色选择的功能权限。
 *
 * <p>数据来源：{@code dbo.sys_permission}（功能权限表）。</p>
 *
 * <p>业务说明：数据库保存代码注册结果，用于角色授权选择；它不代替服务端权限校验。</p>
 *
 * @param permissionCode 权限代码
 * @param permissionName 权限名称
 */
public record PermissionVO(String permissionCode, String permissionName) {
}
