package cn.zqkj.platform.system.identity.mapper;

import cn.zqkj.platform.system.identity.domain.dto.CreateRoleCommand;
import cn.zqkj.platform.system.identity.domain.dto.CreateUserCommand;
import cn.zqkj.platform.system.identity.domain.dto.UpdateRoleCommand;
import cn.zqkj.platform.system.identity.domain.dto.UpdateUserCommand;
import cn.zqkj.platform.system.identity.domain.model.ManagedUserSummary;
import cn.zqkj.platform.system.identity.domain.model.RolePermissionAssignment;
import cn.zqkj.platform.system.identity.domain.model.RoleSummary;
import cn.zqkj.platform.system.identity.domain.model.UserOrganizationAssignment;
import cn.zqkj.platform.system.identity.domain.model.UserRoleAssignment;
import cn.zqkj.platform.system.identity.domain.vo.PermissionVO;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用MyBatis实现用户、角色、权限和机构范围持久化边界。
 */
@Mapper
public interface AccessMapper {

    /**
     * 只查询指定机构范围内的用户基础投影；空范围不返回用户。
     *
     * @param organizationCodes 调用人获准访问的机构代码
     * @return 范围内的用户基础快照
     */
    List<ManagedUserSummary> findUsers(@Param("organizationCodes") List<String> organizationCodes);

    /**
     * 在获准机构范围内读取并锁定用户主机构归属，管理事务结束前不允许并发转移。
     *
     * @param userId 用户主键
     * @param organizationCodes 获准机构代码；空集合拒绝全部行
     * @return 用户存在时的基础快照
     */
    Optional<ManagedUserSummary> findUser(@Param("userId") long userId,
                                         @Param("organizationCodes") List<String> organizationCodes);

    /**
     * 判断规范化登录名是否已被占用。
     *
     * @param loginName 登录名
     * @return 登录名已存在时为true
     */
    boolean userLoginExists(String loginName);

    /**
     * 创建平台用户并返回数据库生成的用户主键。
     *
     * @param command 创建命令
     * @param passwordHash 密码哈希
     * @param actor 操作人
     * @return 新用户主键
     */
    long createUser(
            @Param("command") CreateUserCommand command,
            @Param("passwordHash") String passwordHash,
            @Param("actor") String actor
    );

    /**
     * 使用行版本更新用户显示名称和主机构。
     *
     * @param userId 用户主键
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改行数
     */
    int updateUser(
            @Param("userId") long userId,
            @Param("command") UpdateUserCommand command,
            @Param("actor") String actor
    );

    /**
     * 按并发版本修改用户、角色和权限的启用状态。
     *
     * @param userId 用户主键
     * @param enabled 状态
     * @param expectedVersion 并发版本
     * @param actor 操作人
     * @return 修改行数
     */
    int setUserEnabled(
            @Param("userId") long userId,
            @Param("enabled") boolean enabled,
            @Param("expectedVersion") byte[] expectedVersion,
            @Param("actor") String actor
    );

    /**
     * 重置用户密码并要求其下次登录后修改密码。
     *
     * @param userId 用户主键
     * @param passwordHash 密码哈希
     * @param actor 操作人
     * @return 修改行数
     */
    int resetPassword(
            @Param("userId") long userId,
            @Param("passwordHash") String passwordHash,
            @Param("actor") String actor
    );

    /**
     * 查询角色主键。
     *
     * @param userId 用户主键
     * @return 角色主键
     */
    List<Long> findUserRoleIds(long userId);

    /**
     * 一次读取指定用户的角色关系；空用户集合不返回记录。
     *
     * @param organizationCodes 当前调用人获准访问的机构代码
     * @return 范围内按用户和角色主键稳定排序的授权关系
     */
    List<UserRoleAssignment> findUserRoles(@Param("organizationCodes") List<String> organizationCodes);

    /**
     * 查询机构主键。
     *
     * @param userId 用户主键
     * @return 机构主键
     */
    List<Long> findUserOrganizationIds(long userId);

    /**
     * 一次读取指定用户的显式机构范围；空用户集合不返回记录。
     *
     * @param organizationCodes 当前调用人获准访问的机构代码
     * @return 范围内按用户和机构主键稳定排序的授权关系
     */
    List<UserOrganizationAssignment> findUserOrganizations(@Param("organizationCodes") List<String> organizationCodes);

    /**
     * 判断是否为启用的平台管理员。
     *
     * @param userId 用户主键
     * @return 是否为启用的平台管理员
     */
    boolean isEnabledPlatformAdministrator(long userId);

    /**
     * 统计其他启用平台管理员数量。
     *
     * @param excludedUserId 排除用户主键
     * @return 其他启用平台管理员数量
     */
    int countOtherEnabledPlatformAdministrators(long excludedUserId);

    /**
     * 在同一事务中整体替换用户角色关系。
     *
     * @param userId 用户主键
     * @param roleIds 角色主键
     * @param actor 操作人
     */
    void replaceUserRoles(
            @Param("userId") long userId,
            @Param("roleIds") List<Long> roleIds,
            @Param("actor") String actor
    );

    /**
     * 在同一事务中整体替换用户机构范围关系。
     *
     * @param userId 用户主键
     * @param organizationIds 机构主键
     * @param actor 操作人
     */
    void replaceUserOrganizations(
            @Param("userId") long userId,
            @Param("organizationIds") List<Long> organizationIds,
            @Param("actor") String actor
    );

    /**
     * 查询全部角色及其权限配置。
     *
     * @return 全部角色基础快照
     */
    List<RoleSummary> findRoles();

    /**
     * 查询角色存在时的基础快照。
     *
     * @param roleId 角色主键
     * @return 角色存在时的基础快照
     */
    Optional<RoleSummary> findRole(long roleId);

    /**
     * 判断角色稳定代码是否已被占用。
     *
     * @param roleCode 角色代码
     * @return 角色代码已存在时为true
     */
    boolean roleCodeExists(String roleCode);

    /**
     * 创建角色。
     *
     * @param command 创建命令
     * @param actor 操作人
     * @return 新角色主键
     */
    long createRole(@Param("command") CreateRoleCommand command, @Param("actor") String actor);

    /**
     * 按并发版本更新角色资料。
     *
     * @param roleId 角色主键
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改行数
     */
    int updateRole(
            @Param("roleId") long roleId,
            @Param("command") UpdateRoleCommand command,
            @Param("actor") String actor
    );

    /**
     * 统计使用该角色的用户数量。
     *
     * @param roleId 角色主键
     * @return 使用该角色的用户数量
     */
    int countUsersByRole(long roleId);

    /**
     * 删除角色的全部权限关系，为受控删除角色做准备。
     *
     * @param roleId 角色主键
     */
    void deleteRolePermissions(long roleId);

    /**
     * 删除未被用户引用且不受平台保护的角色。
     *
     * @param roleId 角色主键
     * @param expectedVersion 并发版本
     * @return 删除行数
     */
    int deleteRole(@Param("roleId") long roleId, @Param("expectedVersion") byte[] expectedVersion);

    /**
     * 查询权限代码。
     *
     * @param roleId 角色主键
     * @return 权限代码
     */
    List<String> findRolePermissionCodes(long roleId);

    /**
     * 一次读取角色列表所需的全部功能权限关系。
     *
     * @return 按角色和权限代码稳定排序的授权关系
     */
    List<RolePermissionAssignment> findRolePermissions();

    /**
     * 在同一事务中整体替换角色权限关系。
     *
     * @param roleId 角色主键
     * @param permissionCodes 权限代码
     * @param actor 操作人
     */
    void replaceRolePermissions(
            @Param("roleId") long roleId,
            @Param("permissionCodes") List<String> permissionCodes,
            @Param("actor") String actor
    );

    /**
     * 查询后端已注册的功能权限。
     *
     * @return 已注册权限
     */
    List<PermissionVO> findPermissions();

    /**
     * 统计存在且启用的角色数量。
     *
     * @param roleIds 角色主键
     * @return 存在且启用的角色数量
     */
    int countEnabledRoles(@Param("roleIds") List<Long> roleIds);

    /**
     * 统计存在的权限数量。
     *
     * @param permissionCodes 权限代码
     * @return 存在的权限数量
     */
    int countPermissions(@Param("permissionCodes") List<String> permissionCodes);

    /**
     * 判断是否存在启用的主归属用户。
     *
     * @param organizationId 机构主键
     * @return 是否存在启用的主归属用户
     */
    boolean hasEnabledPrimaryUsers(long organizationId);
}
