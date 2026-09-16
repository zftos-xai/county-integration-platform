package cn.zqkj.platform.system.identity.application;

import java.util.List;
import java.util.Optional;

/**
 * 定义平台本地身份、安全引导和认证所需的持久化边界。
 */
public interface IdentityRepository {

    /**
     * 锁定用户集合并统计用户数，串行化一次性安全引导。
     *
     * @return 当前用户数
     */
    int lockAndCountUsers();

    /**
     * 不加写锁统计平台用户数。
     *
     * @return 当前用户数
     */
    int countUsers();

    /**
     * 按登录名读取认证快照。
     *
     * @param loginName 规范化登录名
     * @return 用户存在时的认证快照
     */
    Optional<UserAccount> findByLoginName(String loginName);

    /**
     * 按用户主键读取认证快照。
     *
     * @param userId 用户主键
     * @return 用户存在时的认证快照
     */
    Optional<UserAccount> findById(long userId);

    /**
     * 读取用户经启用角色获得的权限代码。
     *
     * @param userId 用户主键
     * @return 稳定排序的权限代码
     */
    List<String> findPermissionCodes(long userId);

    /**
     * 注册或更新代码定义的权限名称。
     *
     * @param permissionCode 权限代码
     * @param permissionName 权限名称
     */
    void upsertPermission(String permissionCode, String permissionName);

    /**
     * 创建初始平台管理角色。
     *
     * @param roleCode 角色代码
     * @param roleName 角色名称
     * @param actor 操作主体快照
     * @return 新角色主键
     */
    long createRole(String roleCode, String roleName, String actor);

    /**
     * 创建初始平台用户。
     *
     * @param command 已校验的引导输入
     * @param passwordHash 强密码哈希
     * @param organizationId 主机构主键
     * @param actor 操作主体快照
     * @return 新用户主键
     */
    long createUser(BootstrapCommand command, String passwordHash, long organizationId, String actor);

    /**
     * 把权限授予角色。
     *
     * @param roleId 角色主键
     * @param permissionCode 权限代码
     * @param actor 操作主体快照
     */
    void grantPermission(long roleId, String permissionCode, String actor);

    /**
     * 把角色授予用户。
     *
     * @param userId 用户主键
     * @param roleId 角色主键
     * @param actor 操作主体快照
     */
    void grantRole(long userId, long roleId, String actor);

    /**
     * 把机构数据范围授予用户。
     *
     * @param userId 用户主键
     * @param organizationId 机构主键
     * @param actor 操作主体快照
     */
    void grantOrganization(long userId, long organizationId, String actor);

    /**
     * 修改密码哈希并清除首次改密标记。
     *
     * @param userId 用户主键
     * @param passwordHash 新密码强哈希
     * @param actor 操作主体快照
     * @return 实际修改行数
     */
    int changePassword(long userId, String passwordHash, String actor);
}
