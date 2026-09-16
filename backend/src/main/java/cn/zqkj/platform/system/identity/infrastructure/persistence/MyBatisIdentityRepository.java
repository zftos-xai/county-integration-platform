package cn.zqkj.platform.system.identity.infrastructure.persistence;

import cn.zqkj.platform.system.identity.application.BootstrapCommand;
import cn.zqkj.platform.system.identity.application.IdentityRepository;
import cn.zqkj.platform.system.identity.application.UserAccount;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 使用MyBatis实现平台身份持久化边界。
 */
@Repository
public class MyBatisIdentityRepository implements IdentityRepository {

    private final IdentityMapper mapper;

    /**
     * 创建MyBatis身份仓储。
     *
     * @param mapper 身份MyBatis Mapper
     */
    public MyBatisIdentityRepository(IdentityMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public int lockAndCountUsers() {
        return mapper.lockAndCountUsers();
    }

    /** {@inheritDoc} */
    @Override
    public int countUsers() {
        return mapper.countUsers();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<UserAccount> findByLoginName(String loginName) {
        return Optional.ofNullable(mapper.findByLoginName(loginName));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<UserAccount> findById(long userId) {
        return Optional.ofNullable(mapper.findById(userId));
    }

    /** {@inheritDoc} */
    @Override
    public List<String> findPermissionCodes(long userId) {
        return mapper.findPermissionCodes(userId);
    }

    /** {@inheritDoc} */
    @Override
    public void upsertPermission(String permissionCode, String permissionName) {
        mapper.upsertPermission(permissionCode, permissionName);
    }

    /** {@inheritDoc} */
    @Override
    public long createRole(String roleCode, String roleName, String actor) {
        return mapper.insertRole(roleCode, roleName, actor);
    }

    /** {@inheritDoc} */
    @Override
    public long createUser(BootstrapCommand command, String passwordHash, long organizationId, String actor) {
        return mapper.insertUser(command, passwordHash, organizationId, actor);
    }

    /** {@inheritDoc} */
    @Override
    public void grantPermission(long roleId, String permissionCode, String actor) {
        mapper.insertRolePermission(roleId, permissionCode, actor);
    }

    /** {@inheritDoc} */
    @Override
    public void grantRole(long userId, long roleId, String actor) {
        mapper.insertUserRole(userId, roleId, actor);
    }

    /** {@inheritDoc} */
    @Override
    public void grantOrganization(long userId, long organizationId, String actor) {
        mapper.insertOrganizationScope(userId, organizationId, actor);
    }

    /** {@inheritDoc} */
    @Override
    public int changePassword(long userId, String passwordHash, String actor) {
        return mapper.updatePassword(userId, passwordHash, actor);
    }
}
