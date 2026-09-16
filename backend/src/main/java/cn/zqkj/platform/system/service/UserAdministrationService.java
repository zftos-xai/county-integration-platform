package cn.zqkj.platform.system.service;

import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.dto.CreateUserCommand;
import cn.zqkj.platform.system.domain.vo.ManagedUserVO;
import cn.zqkj.platform.system.domain.dto.UpdateUserCommand;

import java.util.List;

/**
 * 定义平台用户生命周期、角色和机构范围管理服务。
 */
public interface UserAdministrationService {

    /** @param actor 操作人 @return 范围内用户 */
    List<ManagedUserVO> findAll(AccessActor actor);

    /** @param userId 用户主键 @param actor 操作人 @return 用户详情 */
    ManagedUserVO get(long userId, AccessActor actor);

    /** @param command 创建命令 @param actor 操作人 @return 新用户 */
    ManagedUserVO create(CreateUserCommand command, AccessActor actor);

    /** @param userId 用户主键 @param command 修改命令 @param actor 操作人 @return 修改后用户 */
    ManagedUserVO update(long userId, UpdateUserCommand command, AccessActor actor);

    /**
     * @param userId 用户主键
     * @param enabled 目标状态
     * @param expectedVersion 并发版本
     * @param actor 操作人
     * @return 修改后用户
     */
    ManagedUserVO setEnabled(long userId, boolean enabled, byte[] expectedVersion, AccessActor actor);

    /** @param userId 用户主键 @param temporaryPassword 临时密码 @param actor 操作人 */
    void resetPassword(long userId, String temporaryPassword, AccessActor actor);

    /** @param userId 用户主键 @param roleIds 角色主键 @param actor 操作人 @return 修改后用户 */
    ManagedUserVO replaceRoles(long userId, List<Long> roleIds, AccessActor actor);

    /** @param userId 用户主键 @param organizationIds 机构主键 @param actor 操作人 @return 修改后用户 */
    ManagedUserVO replaceOrganizations(long userId, List<Long> organizationIds, AccessActor actor);
}
