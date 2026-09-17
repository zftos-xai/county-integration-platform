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

    /** @return 全部角色及权限 */
    List<RoleVO> findAll();

    /** @param roleId 角色主键 @return 角色详情 */
    RoleVO get(long roleId);

    /** @return 后端注册权限清单 */
    List<PermissionVO> findPermissions();

    /** @param command 创建命令 @param actor 操作主体 @return 新角色 */
    RoleVO create(CreateRoleCommand command, AccessActor actor);

    /** @param roleId 角色主键 @param command 修改命令 @param actor 操作主体 @return 修改后角色 */
    RoleVO update(long roleId, UpdateRoleCommand command, AccessActor actor);

    /** @param roleId 角色主键 @param permissionCodes 权限代码 @param actor 操作主体 @return 修改后角色 */
    RoleVO replacePermissions(long roleId, List<String> permissionCodes, AccessActor actor);
}
