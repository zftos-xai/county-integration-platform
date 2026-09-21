package cn.zqkj.platform.system.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.exception.ResourceNotFoundException;
import cn.zqkj.platform.system.domain.dto.CreateRoleCommand;
import cn.zqkj.platform.system.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.domain.dto.UpdateRoleCommand;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.model.RoleSummary;
import cn.zqkj.platform.system.domain.vo.PermissionVO;
import cn.zqkj.platform.system.domain.vo.RoleVO;
import cn.zqkj.platform.system.mapper.AccessMapper;
import cn.zqkj.platform.system.service.ManagementAuditService;
import cn.zqkj.platform.system.service.RoleAdministrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 实现平台角色、代码注册权限和角色权限关系管理。
 */
@Service
public class RoleAdministrationServiceImpl implements RoleAdministrationService {

    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");
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
        return mapper.findRoles().stream().map(this::enrich).toList();
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
        String roleCode = requireText(command.roleCode(), "roleCode", 64).toUpperCase(Locale.ROOT);
        if (!ROLE_CODE_PATTERN.matcher(roleCode).matches() || "PLATFORM_ADMIN".equals(roleCode)) {
            throw new InvalidRequestException("roleCode 格式无效或属于系统保留值");
        }
        String roleName = requireText(command.roleName(), "roleName", 100);
        if (mapper.roleCodeExists(roleCode)) {
            throw new ResourceConflictException("角色代码已存在");
        }
        long roleId = mapper.createRole(new CreateRoleCommand(roleCode, roleName), actor.loginName());
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
        requireVersion(command.expectedVersion());
        UpdateRoleCommand normalized = new UpdateRoleCommand(
                requireText(command.roleName(), "roleName", 100), command.enabled(), command.expectedVersion()
        );
        if (mapper.updateRole(roleId, normalized, actor.loginName()) != 1) {
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
        if (permissionCodes == null || permissionCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new InvalidRequestException("permissionCodes 中包含无效值");
        }
        List<String> normalized = permissionCodes.stream().map(String::trim).distinct().sorted().toList();
        if (!normalized.isEmpty() && mapper.countPermissions(normalized) != normalized.size()) {
            throw new InvalidRequestException("permissionCodes 中包含后端未登记的权限");
        }
        mapper.replaceRolePermissions(roleId, normalized, actor.loginName());
        RoleVO updated = get(roleId);
        audit(actor, updated, "ROLE_PERMISSIONS_REPLACED", "替换角色权限；权限数量=" + normalized.size());
        return updated;
    }

    /**
     * {@inheritDoc}
     *
     * <p>拒绝删除内置角色或仍被用户引用的角色，并使用行版本避免覆盖并发变更。</p>
     */
    @Transactional
    @Override
    public void delete(long roleId, byte[] expectedVersion, AccessActor actor) {
        RoleSummary current = requireRole(roleId);
        requireMutable(current);
        requireVersion(expectedVersion);
        int assignedUsers = mapper.countUsersByRole(roleId);
        if (assignedUsers > 0) {
            throw new ResourceConflictException("该角色仍分配给 " + assignedUsers + " 个用户，请先调整这些用户的角色");
        }
        mapper.deleteRolePermissions(roleId);
        if (mapper.deleteRole(roleId, expectedVersion) != 1) {
            throw new ResourceConflictException("角色已被他人修改或重新分配，请刷新后重试");
        }
        auditService.recordSuccess(new ManagementAuditCommand(actor, null, null, null, "ROLE_DELETED",
                "ROLE", current.roleCode(), "SUCCESS", "删除未分配给用户的非系统角色",
                ManagementAuditServiceImpl.currentRequestId()));
    }

    /**
     * 追加不包含DDL全文和连接信息的管理审计事件。
     *
     * @param actor 操作人
     * @param role 角色
     * @param action 动作
     * @param summary 不含敏感内容的摘要
     */
    private void audit(AccessActor actor, RoleVO role, String action, String summary) {
        auditService.recordSuccess(new ManagementAuditCommand(actor, null, null, null, action,
                "ROLE", role.roleCode(), "SUCCESS", summary, ManagementAuditServiceImpl.currentRequestId()));
    }

    /**
     * 为角色基础快照补充稳定排序的权限集合。
     *
     * @param summary 角色基础快照
     * @return 完整角色记录
     */
    private RoleVO enrich(RoleSummary summary) {
        return new RoleVO(
                summary.id(), summary.roleCode(), summary.roleName(), summary.enabled(), summary.systemManaged(),
                summary.createdAt(), summary.updatedAt(), summary.version(),
                mapper.findRolePermissionCodes(summary.id())
        );
    }

    /**
     * 读取角色；不存在时抛出资源不存在异常。
     *
     * @param roleId 角色主键
     * @return 存在角色
     */
    private RoleSummary requireRole(long roleId) {
        if (roleId <= 0) {
            throw new InvalidRequestException("roleId 必须大于 0");
        }
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

    /**
     * 校验角色必填文本并返回裁剪后的值。
     *
     * @param value 文本
     * @param field 字段
     * @param maximumLength 最大长度
     * @return 裁剪值
     */
    private String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.trim().length() > maximumLength) {
            throw new InvalidRequestException(field + " is invalid");
        }
        return value.trim();
    }

    /**
     * 校验SQL Server行版本恰好为8字节。
     *
     * @param version 并发版本
     */
    private void requireVersion(byte[] version) {
        if (version == null || version.length != Long.BYTES) {
            throw new InvalidRequestException("version 必须是 8 字节的 SQL Server 行版本号");
        }
    }
}
