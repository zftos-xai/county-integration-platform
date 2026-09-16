package cn.zqkj.platform.system.access.application;

import cn.zqkj.platform.foundation.web.error.InvalidRequestException;
import cn.zqkj.platform.foundation.web.error.ResourceConflictException;
import cn.zqkj.platform.foundation.web.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 实现平台角色、代码注册权限和角色权限关系管理。
 */
@Service
public class RoleAdministrationService {

    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");
    private final AccessRepository repository;

    /**
     * 创建角色管理服务。
     *
     * @param repository 授权持久化边界
     */
    public RoleAdministrationService(AccessRepository repository) {
        this.repository = repository;
    }

    /** @return 全部角色及其权限 */
    @Transactional(readOnly = true)
    public List<RoleView> findAll() {
        return repository.findRoles().stream().map(this::enrich).toList();
    }

    /** @param roleId 角色主键 @return 指定角色及其权限 */
    @Transactional(readOnly = true)
    public RoleView get(long roleId) {
        return enrich(requireRole(roleId));
    }

    /** @return 后端注册权限清单 */
    @Transactional(readOnly = true)
    public List<PermissionView> findPermissions() {
        return repository.findPermissions();
    }

    /**
     * 创建非系统管理角色。
     *
     * @param command 创建命令
     * @param actor 操作人登录名
     * @return 新角色
     */
    @Transactional
    public RoleView create(CreateRoleCommand command, String actor) {
        String roleCode = requireText(command.roleCode(), "roleCode", 64).toUpperCase(Locale.ROOT);
        if (!ROLE_CODE_PATTERN.matcher(roleCode).matches() || "PLATFORM_ADMIN".equals(roleCode)) {
            throw new InvalidRequestException("roleCode has an invalid or protected value");
        }
        String roleName = requireText(command.roleName(), "roleName", 100);
        if (repository.roleCodeExists(roleCode)) {
            throw new ResourceConflictException("Role code already exists");
        }
        long roleId = repository.createRole(new CreateRoleCommand(roleCode, roleName), actor);
        return get(roleId);
    }

    /**
     * 修改非系统角色名称和启用状态。
     *
     * @param roleId 角色主键
     * @param command 修改命令
     * @param actor 操作人登录名
     * @return 修改后角色
     */
    @Transactional
    public RoleView update(long roleId, UpdateRoleCommand command, String actor) {
        RoleSummary current = requireRole(roleId);
        requireMutable(current);
        requireVersion(command.expectedVersion());
        UpdateRoleCommand normalized = new UpdateRoleCommand(
                requireText(command.roleName(), "roleName", 100), command.enabled(), command.expectedVersion()
        );
        if (repository.updateRole(roleId, normalized, actor) != 1) {
            throw new ResourceConflictException("Role version no longer matches");
        }
        return get(roleId);
    }

    /**
     * 原子替换非系统角色的代码注册权限。
     *
     * @param roleId 角色主键
     * @param permissionCodes 目标权限代码
     * @param actor 操作人登录名
     * @return 修改后角色
     */
    @Transactional
    public RoleView replacePermissions(long roleId, List<String> permissionCodes, String actor) {
        RoleSummary current = requireRole(roleId);
        requireMutable(current);
        if (permissionCodes == null || permissionCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new InvalidRequestException("permissionCodes contains an invalid value");
        }
        List<String> normalized = permissionCodes.stream().map(String::trim).distinct().sorted().toList();
        if (!normalized.isEmpty() && repository.countPermissions(normalized) != normalized.size()) {
            throw new InvalidRequestException("permissionCodes contains an unregistered permission");
        }
        repository.replaceRolePermissions(roleId, normalized, actor);
        return get(roleId);
    }

    /** @param summary 角色基础快照 @return 完整角色快照 */
    private RoleView enrich(RoleSummary summary) {
        return new RoleView(
                summary.id(), summary.roleCode(), summary.roleName(), summary.enabled(), summary.systemManaged(),
                summary.createdAt(), summary.updatedAt(), summary.version(),
                repository.findRolePermissionCodes(summary.id())
        );
    }

    /** @param roleId 角色主键 @return 存在角色 */
    private RoleSummary requireRole(long roleId) {
        if (roleId <= 0) {
            throw new InvalidRequestException("roleId must be positive");
        }
        return repository.findRole(roleId)
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
