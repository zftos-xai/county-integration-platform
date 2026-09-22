package cn.zqkj.platform.system.identity.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.exception.ResourceNotFoundException;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import cn.zqkj.platform.system.identity.domain.dto.CreateRoleCommand;
import cn.zqkj.platform.system.identity.domain.dto.UpdateRoleCommand;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.identity.domain.model.RolePermissionAssignment;
import cn.zqkj.platform.system.identity.domain.model.RoleSummary;
import cn.zqkj.platform.system.identity.domain.vo.PermissionVO;
import cn.zqkj.platform.system.identity.domain.vo.RoleVO;
import cn.zqkj.platform.system.identity.mapper.AccessMapper;
import cn.zqkj.platform.system.identity.service.RoleAdministrationService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实现平台角色、代码注册权限和角色权限关系管理。
 */
@Service
public class RoleAdministrationServiceImpl implements RoleAdministrationService {

    private final AccessMapper mapper;
    private final ManagementAuditService auditService;

    /**
     * 创建角色管理服务。
     *
     * @param mapper 授权持久化边界
     * @param auditService 管理审计服务
     */
    public RoleAdministrationServiceImpl(AccessMapper mapper, ManagementAuditService auditService) {
        this.mapper = mapper;
        this.auditService = auditService;
    }

    /**
     * 查询全部角色并补充各自权限集合。
     *
     * @return 全部角色及其权限
     */
    @Transactional(readOnly = true)
    @Override
    public List<RoleVO> findAll() {
        List<RoleSummary> roles = mapper.findRoles();
        if (roles.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> permissionsByRole = new LinkedHashMap<>();
        for (RoleSummary role : roles) {
            permissionsByRole.put(role.id(), new ArrayList<>());
        }
        for (RolePermissionAssignment assignment : mapper.findRolePermissions()) {
            List<String> permissionCodes = permissionsByRole.get(assignment.roleId());
            if (permissionCodes != null) {
                permissionCodes.add(assignment.permissionCode());
            }
        }
        List<RoleVO> result = new ArrayList<>(roles.size());
        for (RoleSummary role : roles) {
            result.add(toView(role, permissionsByRole.get(role.id())));
        }
        return List.copyOf(result);
    }

    /**
     * 读取指定角色并补充权限集合。
     *
     * @param roleId 角色主键
     * @return 指定角色及其权限
     */
    @Transactional(readOnly = true)
    @Override
    public RoleVO get(long roleId) {
        return enrich(requireRole(roleId));
    }

    /**
     * 查询全部代码注册权限。
     *
     * @return 后端注册权限清单
     */
    @Transactional(readOnly = true)
    @Override
    public List<PermissionVO> findPermissions() {
        return mapper.findPermissions();
    }

    /**
     * 创建非系统管理角色。
     *
     * @param command 创建命令
     * @param actor 操作人
     * @return 新角色
     */
    @Transactional
    @Override
    public RoleVO create(CreateRoleCommand command, AccessActor actor) {
        String roleCode = command.roleCode();
        if ("PLATFORM_ADMIN".equals(roleCode)) {
            throw new InvalidRequestException("roleCode 属于系统保留值");
        }
        if (mapper.roleCodeExists(roleCode)) {
            throw new ResourceConflictException("角色代码已存在");
        }
        long roleId = mapper.createRole(command, actor.loginName());
        RoleVO created = get(roleId);
        audit(actor, created, "ROLE_CREATED", "创建角色");
        return created;
    }

    /**
     * 修改非系统角色名称和启用状态。
     *
     * @param roleId 角色主键
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改后角色
     */
    @Transactional
    @Override
    public RoleVO update(long roleId, UpdateRoleCommand command, AccessActor actor) {
        RoleSummary current = requireRole(roleId);
        requireMutable(current);
        if (mapper.updateRole(roleId, command, actor.loginName()) != 1) {
            throw new ResourceConflictException("角色资料已被他人修改，请刷新后重试");
        }
        RoleVO updated = get(roleId);
        audit(actor, updated, "ROLE_UPDATED", "修改角色名称和启用状态");
        return updated;
    }

    /**
     * 原子替换非系统角色的代码注册权限。
     *
     * @param roleId 角色主键
     * @param permissionCodes 目标权限代码
     * @param actor 操作人
     * @return 修改后角色
     */
    @Transactional
    @Override
    public RoleVO replacePermissions(long roleId, List<String> permissionCodes, AccessActor actor) {
        RoleSummary current = requireRole(roleId);
        requireMutable(current);
        List<String> normalized = new ArrayList<>(new TreeSet<>(permissionCodes));
        if (!normalized.isEmpty() && mapper.countPermissions(normalized) != normalized.size()) {
            throw new InvalidRequestException("permissionCodes 中包含后端未登记的权限");
        }
        mapper.replaceRolePermissions(roleId, normalized, actor.loginName());
        RoleVO updated = get(roleId);
        audit(actor, updated, "ROLE_PERMISSIONS_REPLACED", "替换角色权限；权限数量=" + normalized.size());
        return updated;
    }

    /**
     * 删除非内置、无人引用且行版本未变化的角色。
     *
     * <p>拒绝删除内置角色或仍被用户引用的角色，并使用行版本避免覆盖并发变更。</p>
     */
    @Transactional
    @Override
    public void delete(long roleId, byte[] expectedVersion, AccessActor actor) {
        RoleSummary current = requireRole(roleId);
        requireMutable(current);
        int assignedUsers = mapper.countUsersByRole(roleId);
        if (assignedUsers > 0) {
            throw new ResourceConflictException("该角色仍分配给 " + assignedUsers + " 个用户，请先调整这些用户的角色");
        }
        mapper.deleteRolePermissions(roleId);
        if (mapper.deleteRole(roleId, expectedVersion) != 1) {
            throw new ResourceConflictException("角色已被他人修改或重新分配，请刷新后重试");
        }
        auditService.append(new ManagementAuditCommand(actor.userId(), actor.loginName(), null, null, "ROLE_DELETED",
                "ROLE", current.roleCode(), "SUCCESS", "删除未分配给用户的非系统角色",
                auditService.currentRequestId()));
    }

    /**
     * 追加角色维护审计，不记录完整权限清单。
     *
     * @param actor 操作人
     * @param role 角色
     * @param action 动作
     * @param summary 不含敏感内容的摘要
     */
    private void audit(AccessActor actor, RoleVO role, String action, String summary) {
        auditService.append(new ManagementAuditCommand(actor.userId(), actor.loginName(), null, null, action,
                "ROLE", role.roleCode(), "SUCCESS", summary, auditService.currentRequestId()));
    }

    /**
     * 为角色基础快照补充稳定排序的权限集合。
     *
     * @param summary 角色基础快照
     * @return 完整角色记录
     */
    private RoleVO enrich(RoleSummary summary) {
        return toView(summary, mapper.findRolePermissionCodes(summary.id()));
    }

    /**
     * 使用已读取的权限代码组装管理端角色视图。
     *
     * @param summary 角色基础快照
     * @param permissionCodes 角色获授的功能权限代码
     * @return 不含数据库内部授权关系行的角色视图
     */
    private RoleVO toView(RoleSummary summary, List<String> permissionCodes) {
        return new RoleVO(
                summary.id(), summary.roleCode(), summary.roleName(), summary.enabled(), summary.systemManaged(),
                summary.createdAt(), summary.updatedAt(), summary.version(),
                permissionCodes
        );
    }

    /**
     * 读取角色；不存在时抛出资源不存在异常。
     *
     * @param roleId 角色主键
     * @return 存在角色
     */
    private RoleSummary requireRole(long roleId) {
        return mapper.findRole(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Platform role was not found"));
    }

    /**
     * 拒绝修改或删除平台保护角色。
     *
     * @param role 角色
     */
    private void requireMutable(RoleSummary role) {
        if (role.systemManaged()) {
            throw new ResourceConflictException("系统保护角色不能修改");
        }
    }

}
