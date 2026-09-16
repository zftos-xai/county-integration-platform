package cn.zqkj.platform.system.access.application;

import java.util.List;
import java.util.Optional;

/**
 * 定义用户、角色、功能权限和机构范围管理的持久化边界。
 */
public interface AccessRepository {

    /** @return 全部用户基础快照 */
    List<ManagedUserSummary> findUsers();

    /**
     * @param userId 用户主键
     * @return 用户存在时的基础快照
     */
    Optional<ManagedUserSummary> findUser(long userId);

    /**
     * @param loginName 登录名
     * @return 登录名已存在时为true
     */
    boolean userLoginExists(String loginName);

    /**
     * @param command 已校验创建命令
     * @param passwordHash 临时密码强哈希
     * @param actor 操作主体
     * @return 新用户主键
     */
    long createUser(CreateUserCommand command, String passwordHash, String actor);

    /**
     * @param userId 用户主键
     * @param command 修改命令
     * @param actor 操作主体
     * @return 实际修改行数
     */
    int updateUser(long userId, UpdateUserCommand command, String actor);

    /**
     * @param userId 用户主键
     * @param enabled 目标状态
     * @param expectedVersion 并发版本
     * @param actor 操作主体
     * @return 实际修改行数
     */
    int setUserEnabled(long userId, boolean enabled, byte[] expectedVersion, String actor);

    /**
     * @param userId 用户主键
     * @param passwordHash 新临时密码强哈希
     * @param actor 操作主体
     * @return 实际修改行数
     */
    int resetPassword(long userId, String passwordHash, String actor);

    /** @param userId 用户主键 @return 已授予角色主键 */
    List<Long> findUserRoleIds(long userId);

    /** @param userId 用户主键 @return 已授予机构范围主键 */
    List<Long> findUserOrganizationIds(long userId);

    /**
     * 使用锁读取用户是否为启用的平台管理员。
     *
     * @param userId 用户主键
     * @return 是启用平台管理员时为true
     */
    boolean isEnabledPlatformAdministrator(long userId);

    /**
     * 使用锁统计目标用户之外的启用平台管理员。
     *
     * @param excludedUserId 排除的用户主键
     * @return 其他启用平台管理员数量
     */
    int countOtherEnabledPlatformAdministrators(long excludedUserId);

    /**
     * 原子替换用户角色。
     *
     * @param userId 用户主键
     * @param roleIds 目标角色主键
     * @param actor 操作主体
     */
    void replaceUserRoles(long userId, List<Long> roleIds, String actor);

    /**
     * 原子替换用户机构范围。
     *
     * @param userId 用户主键
     * @param organizationIds 目标机构主键
     * @param actor 操作主体
     */
    void replaceUserOrganizations(long userId, List<Long> organizationIds, String actor);

    /** @return 全部角色基础快照 */
    List<RoleSummary> findRoles();

    /** @param roleId 角色主键 @return 角色存在时的基础快照 */
    Optional<RoleSummary> findRole(long roleId);

    /** @param roleCode 角色代码 @return 角色代码已存在时为true */
    boolean roleCodeExists(String roleCode);

    /**
     * @param command 已校验创建命令
     * @param actor 操作主体
     * @return 新角色主键
     */
    long createRole(CreateRoleCommand command, String actor);

    /**
     * @param roleId 角色主键
     * @param command 修改命令
     * @param actor 操作主体
     * @return 实际修改行数
     */
    int updateRole(long roleId, UpdateRoleCommand command, String actor);

    /** @param roleId 角色主键 @return 已授予权限代码 */
    List<String> findRolePermissionCodes(long roleId);

    /**
     * 原子替换非系统角色权限。
     *
     * @param roleId 角色主键
     * @param permissionCodes 目标权限代码
     * @param actor 操作主体
     */
    void replaceRolePermissions(long roleId, List<String> permissionCodes, String actor);

    /** @return 代码注册权限清单 */
    List<PermissionView> findPermissions();

    /** @param roleIds 待核对角色主键 @return 存在且启用的匹配数量 */
    int countEnabledRoles(List<Long> roleIds);

    /** @param permissionCodes 待核对权限代码 @return 存在的匹配数量 */
    int countPermissions(List<String> permissionCodes);

    /**
     * 判定机构是否仍有启用用户作为主归属。
     *
     * @param organizationId 机构主键
     * @return 存在时为true
     */
    boolean hasEnabledPrimaryUsers(long organizationId);
}
