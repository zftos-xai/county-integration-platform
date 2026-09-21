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

    /**
     * 查询当前操作人机构范围内可管理的用户。
     *
     * @param actor 操作人
     * @return 范围内用户
     */
    List<ManagedUserVO> findAll(AccessActor actor);

    /**
     * 按主键读取平台用户；不存在时由调用边界按约定处理。
     *
     * @param userId 用户主键
     * @param actor 操作人
     * @return 用户详情
     */
    ManagedUserVO get(long userId, AccessActor actor);

    /**
     * 创建平台用户并返回最新视图。
     *
     * @param command 创建命令
     * @param actor 操作人
     * @return 新用户
     */
    ManagedUserVO create(CreateUserCommand command, AccessActor actor);

    /**
     * 按并发版本更新平台用户并返回最新视图。
     *
     * @param userId 用户主键
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改后用户
     */
    ManagedUserVO update(long userId, UpdateUserCommand command, AccessActor actor);

    /**
     * 按并发版本修改平台用户的启用状态。
     *
     * @param userId 用户主键
     * @param enabled 目标状态
     * @param expectedVersion 并发版本
     * @param actor 操作人
     * @return 修改后用户
     */
    ManagedUserVO setEnabled(long userId, boolean enabled, byte[] expectedVersion, AccessActor actor);

    /**
     * 重置用户密码并要求其下次登录后修改密码。
     *
     * @param userId 用户主键
     * @param temporaryPassword 临时密码
     * @param actor 操作人
     */
    void resetPassword(long userId, String temporaryPassword, AccessActor actor);

    /**
     * 整体替换用户角色集合。
     *
     * @param userId 用户主键
     * @param roleIds 角色主键
     * @param actor 操作人
     * @return 修改后用户
     */
    ManagedUserVO replaceRoles(long userId, List<Long> roleIds, AccessActor actor);

    /**
     * 整体替换用户的机构数据范围。
     *
     * @param userId 用户主键
     * @param organizationIds 机构主键
     * @param actor 操作人
     * @return 修改后用户
     */
    ManagedUserVO replaceOrganizations(long userId, List<Long> organizationIds, AccessActor actor);
}
