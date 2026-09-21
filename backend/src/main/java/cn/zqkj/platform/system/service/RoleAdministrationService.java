package cn.zqkj.platform.system.service;

import cn.zqkj.platform.system.domain.dto.CreateRoleCommand;
import cn.zqkj.platform.system.domain.dto.UpdateRoleCommand;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.vo.PermissionVO;
import cn.zqkj.platform.system.domain.vo.RoleVO;

import java.util.List;

/**
 * 定义平台角色和功能权限管理服务。
 */
public interface RoleAdministrationService {

    /**
     * 查询全部角色及其功能权限集合。
     *
     * @return 全部角色及权限
     */
    List<RoleVO> findAll();

    /**
     * 按主键读取角色和权限；不存在时由调用边界按约定处理。
     *
     * @param roleId 角色主键
     * @return 角色详情
     */
    RoleVO get(long roleId);

    /**
     * 查询后端已注册的功能权限。
     *
     * @return 后端注册权限清单
     */
    List<PermissionVO> findPermissions();

    /**
     * 创建角色和权限并返回最新视图。
     *
     * @param command 创建命令
     * @param actor 操作人
     * @return 新角色
     */
    RoleVO create(CreateRoleCommand command, AccessActor actor);

    /**
     * 按并发版本更新角色和权限并返回最新视图。
     *
     * @param roleId 角色主键
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改后角色
     */
    RoleVO update(long roleId, UpdateRoleCommand command, AccessActor actor);

    /**
     * 整体替换角色的功能权限集合。
     *
     * @param roleId 角色主键
     * @param permissionCodes 权限代码
     * @param actor 操作人
     * @return 修改后角色
     */
    RoleVO replacePermissions(long roleId, List<String> permissionCodes, AccessActor actor);

    /**
     * 删除未被用户引用且不受平台保护的角色。
     *
     * @param roleId 角色主键
     * @param expectedVersion 并发版本
     * @param actor 操作人
     */
    void delete(long roleId, byte[] expectedVersion, AccessActor actor);
}
