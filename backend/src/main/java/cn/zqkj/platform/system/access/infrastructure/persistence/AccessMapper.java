package cn.zqkj.platform.system.access.infrastructure.persistence;

import cn.zqkj.platform.system.access.application.AccessRepository;
import cn.zqkj.platform.system.access.application.CreateRoleCommand;
import cn.zqkj.platform.system.access.application.CreateUserCommand;
import cn.zqkj.platform.system.access.application.UpdateRoleCommand;
import cn.zqkj.platform.system.access.application.UpdateUserCommand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 使用MyBatis实现用户、角色、权限和机构范围持久化边界。
 */
@Mapper
public interface AccessMapper extends AccessRepository {

    /** {@inheritDoc} */
    @Override
    long createUser(
            @Param("command") CreateUserCommand command,
            @Param("passwordHash") String passwordHash,
            @Param("actor") String actor
    );

    /** {@inheritDoc} */
    @Override
    int updateUser(
            @Param("userId") long userId,
            @Param("command") UpdateUserCommand command,
            @Param("actor") String actor
    );

    /** {@inheritDoc} */
    @Override
    int setUserEnabled(
            @Param("userId") long userId,
            @Param("enabled") boolean enabled,
            @Param("expectedVersion") byte[] expectedVersion,
            @Param("actor") String actor
    );

    /** {@inheritDoc} */
    @Override
    int resetPassword(
            @Param("userId") long userId,
            @Param("passwordHash") String passwordHash,
            @Param("actor") String actor
    );

    /** {@inheritDoc} */
    @Override
    void replaceUserRoles(
            @Param("userId") long userId,
            @Param("roleIds") List<Long> roleIds,
            @Param("actor") String actor
    );

    /** {@inheritDoc} */
    @Override
    void replaceUserOrganizations(
            @Param("userId") long userId,
            @Param("organizationIds") List<Long> organizationIds,
            @Param("actor") String actor
    );

    /** {@inheritDoc} */
    @Override
    long createRole(@Param("command") CreateRoleCommand command, @Param("actor") String actor);

    /** {@inheritDoc} */
    @Override
    int updateRole(
            @Param("roleId") long roleId,
            @Param("command") UpdateRoleCommand command,
            @Param("actor") String actor
    );

    /** {@inheritDoc} */
    @Override
    void replaceRolePermissions(
            @Param("roleId") long roleId,
            @Param("permissionCodes") List<String> permissionCodes,
            @Param("actor") String actor
    );

    /** {@inheritDoc} */
    @Override
    int countEnabledRoles(@Param("roleIds") List<Long> roleIds);

    /** {@inheritDoc} */
    @Override
    int countPermissions(@Param("permissionCodes") List<String> permissionCodes);
}
