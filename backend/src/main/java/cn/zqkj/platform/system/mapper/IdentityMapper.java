package cn.zqkj.platform.system.mapper;

import cn.zqkj.platform.system.domain.dto.BootstrapCommand;
import cn.zqkj.platform.system.domain.model.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 使用MyBatis读写平台本地身份和初始授权关系。
 */
@Mapper
public interface IdentityMapper {

    /** @return 持有更新范围锁时的平台用户数量 */
    int lockAndCountUsers();

    /** @return 平台用户数量 */
    int countUsers();

    /**
     * @param loginName 规范化登录名
     * @return 用户认证快照；不存在时为空
     */
    UserAccount findByLoginName(String loginName);

    /**
     * @param userId 用户主键
     * @return 用户认证快照；不存在时为空
     */
    UserAccount findById(long userId);

    /**
     * @param userId 用户主键
     * @return 经启用角色获得的权限代码
     */
    List<String> findPermissionCodes(long userId);

    /**
     * @param userId 用户主键
     * @return 已启用机构范围代码
     */
    List<String> findOrganizationCodes(long userId);

    /**
     * @param permissionCode 权限代码
     * @param permissionName 权限名称
     * @return 实际影响行数
     */
    int upsertPermission(@Param("permissionCode") String permissionCode, @Param("permissionName") String permissionName);

    /**
     * @param roleCode 角色代码
     * @param roleName 角色名称
     * @param actor 操作主体快照
     * @return 新角色主键
     */
    long createRole(@Param("roleCode") String roleCode, @Param("roleName") String roleName, @Param("actor") String actor);

    /**
     * @param command 安全引导输入
     * @param passwordHash 密码强哈希
     * @param organizationId 主机构主键
     * @param actor 操作主体快照
     * @return 新用户主键
     */
    long createUser(
            @Param("command") BootstrapCommand command,
            @Param("passwordHash") String passwordHash,
            @Param("organizationId") long organizationId,
            @Param("actor") String actor
    );

    /**
     * @param roleId 角色主键
     * @param permissionCode 权限代码
     * @param actor 操作主体快照
     * @return 实际影响行数
     */
    int grantPermission(
            @Param("roleId") long roleId,
            @Param("permissionCode") String permissionCode,
            @Param("actor") String actor
    );

    /**
     * @param userId 用户主键
     * @param roleId 角色主键
     * @param actor 操作主体快照
     * @return 实际影响行数
     */
    int grantRole(@Param("userId") long userId, @Param("roleId") long roleId, @Param("actor") String actor);

    /**
     * @param userId 用户主键
     * @param organizationId 机构主键
     * @param actor 操作主体快照
     * @return 实际影响行数
     */
    int grantOrganization(
            @Param("userId") long userId,
            @Param("organizationId") long organizationId,
            @Param("actor") String actor
    );

    /**
     * @param userId 用户主键
     * @param passwordHash 新密码强哈希
     * @param actor 操作主体快照
     * @return 实际影响行数
     */
    int changePassword(
            @Param("userId") long userId,
            @Param("passwordHash") String passwordHash,
            @Param("actor") String actor
    );
}
