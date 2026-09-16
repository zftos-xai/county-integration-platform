package cn.zqkj.platform.system.access.application;

import cn.zqkj.platform.foundation.web.error.InvalidRequestException;
import cn.zqkj.platform.foundation.web.error.ResourceConflictException;
import cn.zqkj.platform.foundation.web.error.ResourceNotFoundException;
import cn.zqkj.platform.modules.organization.application.OrganizationService;
import cn.zqkj.platform.modules.organization.application.OrganizationView;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 实现平台用户生命周期、角色分配和机构数据范围管理。
 */
@Service
public class UserAdministrationService {

    private static final Pattern LOGIN_PATTERN = Pattern.compile("[A-Za-z0-9._-]{3,64}");
    private static final int MINIMUM_PASSWORD_LENGTH = 12;
    private static final int MAXIMUM_PASSWORD_LENGTH = 128;
    private final AccessRepository repository;
    private final OrganizationService organizationService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建用户管理服务。
     *
     * @param repository 授权持久化边界
     * @param organizationService 机构应用服务
     * @param passwordEncoder 密码哈希器
     */
    public UserAdministrationService(
            AccessRepository repository,
            OrganizationService organizationService,
            PasswordEncoder passwordEncoder
    ) {
        this.repository = repository;
        this.organizationService = organizationService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 查询操作人机构范围内的用户。
     *
     * @param actor 操作人上下文
     * @return 用户快照
     */
    @Transactional(readOnly = true)
    public List<ManagedUserView> findAll(AccessActor actor) {
        return repository.findUsers().stream()
                .filter(user -> actor.canAccess(user.organizationCode()))
                .map(this::enrich)
                .toList();
    }

    /**
     * 查询机构范围内的指定用户。
     *
     * @param userId 用户主键
     * @param actor 操作人上下文
     * @return 用户快照
     */
    @Transactional(readOnly = true)
    public ManagedUserView get(long userId, AccessActor actor) {
        ManagedUserSummary user = requireUser(userId);
        requireAccess(actor, user.organizationCode());
        return enrich(user);
    }

    /**
     * 创建归属获批机构且必须首次改密的平台用户。
     *
     * @param command 创建命令
     * @param actor 操作人上下文
     * @return 新用户快照
     */
    @Transactional
    public ManagedUserView create(CreateUserCommand command, AccessActor actor) {
        String loginName = normalizeLogin(command.loginName());
        String displayName = requireText(command.displayName(), "displayName", 100);
        validatePassword(command.temporaryPassword(), loginName);
        OrganizationView organization = requireEnabledOrganization(command.primaryOrganizationId());
        requireAccess(actor, organization.organizationCode());
        if (repository.userLoginExists(loginName)) {
            throw new ResourceConflictException("User login name already exists");
        }
        CreateUserCommand normalized = new CreateUserCommand(
                loginName, displayName, organization.id(), command.temporaryPassword()
        );
        long userId = repository.createUser(
                normalized, passwordEncoder.encode(command.temporaryPassword()), actor.loginName()
        );
        repository.replaceUserOrganizations(userId, List.of(organization.id()), actor.loginName());
        return get(userId, actor);
    }

    /**
     * 修改用户显示名称和主机构。
     *
     * @param userId 用户主键
     * @param command 修改命令
     * @param actor 操作人上下文
     * @return 修改后用户快照
     */
    @Transactional
    public ManagedUserView update(long userId, UpdateUserCommand command, AccessActor actor) {
        ManagedUserSummary current = requireUser(userId);
        requireAccess(actor, current.organizationCode());
        OrganizationView organization = requireEnabledOrganization(command.primaryOrganizationId());
        requireAccess(actor, organization.organizationCode());
        requireVersion(command.expectedVersion());
        UpdateUserCommand normalized = new UpdateUserCommand(
                requireText(command.displayName(), "displayName", 100), organization.id(), command.expectedVersion()
        );
        if (repository.updateUser(userId, normalized, actor.loginName()) != 1) {
            throw new ResourceConflictException("User version no longer matches");
        }
        List<Long> scopes = repository.findUserOrganizationIds(userId);
        if (!scopes.contains(organization.id())) {
            java.util.ArrayList<Long> updatedScopes = new java.util.ArrayList<>(scopes);
            updatedScopes.add(organization.id());
            repository.replaceUserOrganizations(userId, distinctPositiveIds(updatedScopes), actor.loginName());
        }
        return get(userId, actor);
    }

    /**
     * 启用或停用用户并保护最后一个平台管理员。
     *
     * @param userId 用户主键
     * @param enabled 目标状态
     * @param expectedVersion 并发版本
     * @param actor 操作人上下文
     * @return 修改后用户快照
     */
    @Transactional
    public ManagedUserView setEnabled(long userId, boolean enabled, byte[] expectedVersion, AccessActor actor) {
        ManagedUserSummary current = requireUser(userId);
        requireAccess(actor, current.organizationCode());
        requireVersion(expectedVersion);
        if (!enabled && repository.isEnabledPlatformAdministrator(userId)
                && repository.countOtherEnabledPlatformAdministrators(userId) == 0) {
            throw new ResourceConflictException("The last enabled platform administrator cannot be disabled");
        }
        if (repository.setUserEnabled(userId, enabled, expectedVersion, actor.loginName()) != 1) {
            throw new ResourceConflictException("User version no longer matches");
        }
        return get(userId, actor);
    }

    /**
     * 为机构范围内用户设置新的临时密码并强制下次登录改密。
     *
     * @param userId 用户主键
     * @param temporaryPassword 新临时密码
     * @param actor 操作人上下文
     */
    @Transactional
    public void resetPassword(long userId, String temporaryPassword, AccessActor actor) {
        ManagedUserSummary current = requireUser(userId);
        requireAccess(actor, current.organizationCode());
        validatePassword(temporaryPassword, current.loginName());
        if (repository.resetPassword(
                userId, passwordEncoder.encode(temporaryPassword), actor.loginName()
        ) != 1) {
            throw new ResourceConflictException("User password reset failed");
        }
    }

    /**
     * 原子替换用户角色并保护最后一个平台管理员。
     *
     * @param userId 用户主键
     * @param roleIds 目标角色主键
     * @param actor 操作人上下文
     * @return 修改后用户快照
     */
    @Transactional
    public ManagedUserView replaceRoles(long userId, List<Long> roleIds, AccessActor actor) {
        ManagedUserSummary current = requireUser(userId);
        requireAccess(actor, current.organizationCode());
        List<Long> normalized = distinctPositiveIds(roleIds);
        if (!normalized.isEmpty() && repository.countEnabledRoles(normalized) != normalized.size()) {
            throw new InvalidRequestException("roleIds contains a missing or disabled role");
        }
        Long platformAdminRoleId = repository.findRoles().stream()
                .filter(role -> "PLATFORM_ADMIN".equals(role.roleCode()))
                .map(RoleSummary::id)
                .findFirst()
                .orElse(null);
        if (repository.isEnabledPlatformAdministrator(userId)
                && !normalized.contains(platformAdminRoleId)
                && repository.countOtherEnabledPlatformAdministrators(userId) == 0) {
            throw new ResourceConflictException("The last enabled platform administrator must retain its role");
        }
        repository.replaceUserRoles(userId, normalized, actor.loginName());
        return get(userId, actor);
    }

    /**
     * 原子替换用户机构范围；范围必须包含主机构且不能超出操作人范围。
     *
     * @param userId 用户主键
     * @param organizationIds 目标机构主键
     * @param actor 操作人上下文
     * @return 修改后用户快照
     */
    @Transactional
    public ManagedUserView replaceOrganizations(
            long userId,
            List<Long> organizationIds,
            AccessActor actor
    ) {
        ManagedUserSummary current = requireUser(userId);
        requireAccess(actor, current.organizationCode());
        List<Long> normalized = distinctPositiveIds(organizationIds);
        if (!normalized.contains(current.primaryOrganizationId())) {
            throw new InvalidRequestException("Organization scope must include the primary organization");
        }
        for (Long organizationId : normalized) {
            OrganizationView organization = requireEnabledOrganization(organizationId);
            requireAccess(actor, organization.organizationCode());
        }
        repository.replaceUserOrganizations(userId, normalized, actor.loginName());
        return get(userId, actor);
    }

    /**
     * 组装用户及关联关系快照。
     *
     * @param user 用户基础快照
     * @return 完整用户快照
     */
    private ManagedUserView enrich(ManagedUserSummary user) {
        return new ManagedUserView(
                user.id(), user.loginName(), user.displayName(), user.primaryOrganizationId(),
                user.organizationCode(), user.enabled(), user.mustChangePassword(), user.createdAt(),
                user.updatedAt(), user.version(), repository.findUserRoleIds(user.id()),
                repository.findUserOrganizationIds(user.id())
        );
    }

    /** @param userId 用户主键 @return 存在的用户 */
    private ManagedUserSummary requireUser(long userId) {
        if (userId <= 0) {
            throw new InvalidRequestException("userId must be positive");
        }
        return repository.findUser(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Platform user was not found"));
    }

    /** @param organizationId 机构主键 @return 已启用机构 */
    private OrganizationView requireEnabledOrganization(long organizationId) {
        OrganizationView organization = organizationService.get(organizationId);
        if (!organization.enabled()) {
            throw new ResourceConflictException("Organization is disabled");
        }
        return organization;
    }

    /** @param actor 操作人 @param organizationCode 机构代码 */
    private void requireAccess(AccessActor actor, String organizationCode) {
        if (!actor.canAccess(organizationCode)) {
            throw new AccessDeniedException("Organization access is not permitted");
        }
    }

    /** @param value 登录名 @return 规范化登录名 */
    private String normalizeLogin(String value) {
        String loginName = requireText(value, "loginName", 64).toLowerCase(Locale.ROOT);
        if (!LOGIN_PATTERN.matcher(loginName).matches()) {
            throw new InvalidRequestException("loginName has an invalid format");
        }
        return loginName;
    }

    /** @param password 密码 @param loginName 登录名 */
    private void validatePassword(String password, String loginName) {
        if (password == null || password.length() < MINIMUM_PASSWORD_LENGTH
                || password.length() > MAXIMUM_PASSWORD_LENGTH) {
            throw new InvalidRequestException("Password length is outside the allowed range");
        }
        if (password.toLowerCase(Locale.ROOT).contains(loginName.toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestException("Password must not contain the login name");
        }
    }

    /** @param value 文本 @param field 字段 @param maximumLength 最大长度 @return 裁剪值 */
    private String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.trim().length() > maximumLength) {
            throw new InvalidRequestException(field + " is invalid");
        }
        return value.trim();
    }

    /** @param ids 原始主键 @return 去重后的正数主键 */
    private List<Long> distinctPositiveIds(List<Long> ids) {
        if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new InvalidRequestException("Identifier list contains an invalid value");
        }
        return ids.stream().distinct().sorted().toList();
    }

    /** @param version 并发版本 */
    private void requireVersion(byte[] version) {
        if (version == null || version.length != Long.BYTES) {
            throw new InvalidRequestException("version must be an 8-byte rowversion value");
        }
    }
}
