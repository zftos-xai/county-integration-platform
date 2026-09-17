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

    /** @return 全部角色及其权限 */
    @Transactional(readOnly = true)
    @Override
    public List<RoleVO> findAll() {
        return mapper.findRoles().stream().map(this::enrich).toList();
    }

    /** @param roleId 角色主键 @return 指定角色及其权限 */
    @Transactional(readOnly = true)
    @Override
    public RoleVO get(long roleId) {
        return enrich(requireRole(roleId));
    }

    /** @return 后端注册权限清单 */
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
            throw new InvalidRequestException("roleCode has an invalid or protected value");
        }
        String roleName = requireText(command.roleName(), "roleName", 100);
        if (mapper.roleCodeExists(roleCode)) {
            throw new ResourceConflictException("Role code already exists");
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
            throw new ResourceConflictException("Role version no longer matches");
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
            throw new InvalidRequestException("permissionCodes contains an invalid value");
        }
        List<String> normalized = permissionCodes.stream().map(String::trim).distinct().sorted().toList();
        if (!normalized.isEmpty() && mapper.countPermissions(normalized) != normalized.size()) {
            throw new InvalidRequestException("permissionCodes contains an unregistered permission");
        }
        mapper.replaceRolePermissions(roleId, normalized, actor.loginName());
        RoleVO updated = get(roleId);
        audit(actor, updated, "ROLE_PERMISSIONS_REPLACED", "替换角色权限；权限数量=" + normalized.size());
        return updated;
    }

    /** @param actor 操作人 @param role 角色 @param action 动作 @param summary 脱敏摘要 */
    private void audit(AccessActor actor, RoleVO role, String action, String summary) {
        auditService.recordSuccess(new ManagementAuditCommand(actor, null, null, null, action,
                "ROLE", role.roleCode(), "SUCCESS", summary, ManagementAuditServiceImpl.currentRequestId()));
    }

    /** @param summary 角色基础快照 @return 完整角色快照 */
    private RoleVO enrich(RoleSummary summary) {
        return new RoleVO(
                summary.id(), summary.roleCode(), summary.roleName(), summary.enabled(), summary.systemManaged(),
                summary.createdAt(), summary.updatedAt(), summary.version(),
                mapper.findRolePermissionCodes(summary.id())
        );
    }

    /** @param roleId 角色主键 @return 存在角色 */
    private RoleSummary requireRole(long roleId) {
        if (roleId <= 0) {
            throw new InvalidRequestException("roleId must be positive");
        }
        return mapper.findRole(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Platform role was not found"));
    }

    /** @param role 角色 */
    private void requireMutable(RoleSummary role) {
        if (role.systemManaged()) {
            throw new ResourceConflictException("System-managed role cannot be modified");
        }
    }

    /** @param value 文本 @param field 字段 @param maximumLength 最大长度 @return 裁剪值 */
    private String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.trim().length() > maximumLength) {
            throw new InvalidRequestException(field + " is invalid");
        }
        return value.trim();
    }

    /** @param version 并发版本 */
    private void requireVersion(byte[] version) {
        if (version == null || version.length != Long.BYTES) {
            throw new InvalidRequestException("version must be an 8-byte rowversion value");
        }
    }
}
