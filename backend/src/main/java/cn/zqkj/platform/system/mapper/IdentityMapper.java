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

    /**
     * 持有更新范围锁统计平台用户数，用于串行化首次安全引导。
     *
     * @return 持有更新范围锁时的平台用户数量
     */
    int lockAndCountUsers();

    /**
     * 统计平台用户数量。
     *
     * @return 平台用户数量
     */
    int countUsers();

    /**
     * 按规范化登录名读取用户认证快照。
     *
     * @param loginName 规范化登录名
     * @return 用户认证快照；不存在时为空
     */
    UserAccount findByLoginName(String loginName);

    /**
     * 按主键读取身份和初始授权；不存在时由调用边界按约定处理。
     *
     * @param userId 用户主键
     * @return 用户认证快照；不存在时为空
     */
    UserAccount findById(long userId);

    /**
     * 查询经启用角色获得的权限代码。
     *
     * @param userId 用户主键
     * @return 经启用角色获得的权限代码
     */
    List<String> findPermissionCodes(long userId);

    /**
     * 查询已启用机构范围代码。
     *
     * @param userId 用户主键
     * @return 已启用机构范围代码
     */
    List<String> findOrganizationCodes(long userId);

    /**
     * 按权限代码新增或更新代码注册的功能权限。
     *
     * @param permissionCode 权限代码
     * @param permissionName 权限名称
     * @return 实际影响行数
     */
    int upsertPermission(@Param("permissionCode") String permissionCode, @Param("permissionName") String permissionName);

    /**
     * 创建角色。
     *
     * @param roleCode 角色代码
     * @param roleName 角色名称
     * @param actor 操作时记录的用户信息
     * @return 新角色主键
     */
    long createRole(@Param("roleCode") String roleCode, @Param("roleName") String roleName, @Param("actor") String actor);

    /**
     * 创建平台用户并返回数据库生成的用户主键。
     *
     * @param command 安全引导输入
     * @param passwordHash 密码强哈希
     * @param organizationId 主机构主键
     * @param actor 操作时记录的用户信息
     * @return 新用户主键
     */
    long createUser(
            @Param("command") BootstrapCommand command,
            @Param("passwordHash") String passwordHash,
            @Param("organizationId") long organizationId,
            @Param("actor") String actor
    );

    /**
     * 向初始角色授予一项代码注册权限。
     *
     * @param roleId 角色主键
     * @param permissionCode 权限代码
     * @param actor 操作时记录的用户信息
     * @return 实际影响行数
     */
    int grantPermission(
            @Param("roleId") long roleId,
            @Param("permissionCode") String permissionCode,
            @Param("actor") String actor
    );

    /**
     * 建立用户与角色的授权关系。
     *
     * @param userId 用户主键
     * @param roleId 角色主键
     * @param actor 操作时记录的用户信息
     * @return 实际影响行数
     */
    int grantRole(@Param("userId") long userId, @Param("roleId") long roleId, @Param("actor") String actor);

    /**
     * 建立用户可访问机构的数据范围关系。
     *
     * @param userId 用户主键
     * @param organizationId 机构主键
     * @param actor 操作时记录的用户信息
     * @return 实际影响行数
     */
    int grantOrganization(
            @Param("userId") long userId,
            @Param("organizationId") long organizationId,
            @Param("actor") String actor
    );

    /**
     * 校验旧密码后修改当前用户密码并解除强制改密状态。
     *
     * @param userId 用户主键
     * @param passwordHash 新密码强哈希
     * @param actor 操作时记录的用户信息
     * @return 实际影响行数
     */
    int changePassword(
            @Param("userId") long userId,
            @Param("passwordHash") String passwordHash,
            @Param("actor") String actor
    );
}
