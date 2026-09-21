package cn.zqkj.platform.system.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.exception.ResourceNotFoundException;
import cn.zqkj.platform.system.domain.dto.CreateUserCommand;
import cn.zqkj.platform.system.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.domain.dto.UpdateUserCommand;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.model.ManagedUserSummary;
import cn.zqkj.platform.system.domain.model.RoleSummary;
import cn.zqkj.platform.system.domain.vo.ManagedUserVO;
import cn.zqkj.platform.system.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.mapper.AccessMapper;
import cn.zqkj.platform.system.service.ManagementAuditService;
import cn.zqkj.platform.system.service.OrganizationService;
import cn.zqkj.platform.system.service.UserAdministrationService;
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
public class UserAdministrationServiceImpl implements UserAdministrationService {

    private static final Pattern LOGIN_PATTERN = Pattern.compile("[A-Za-z0-9._-]{3,64}");
    private static final int MINIMUM_PASSWORD_LENGTH = 9;
    private static final int MAXIMUM_PASSWORD_LENGTH = 128;
    private final AccessMapper mapper;
    private final OrganizationService organizationService;
    private final PasswordEncoder passwordEncoder;
    private final ManagementAuditService auditService;

    /**
     * 创建用户管理服务。
     *
     * @param mapper 授权持久化边界
     * @param organizationService 机构应用服务
     * @param passwordEncoder 密码哈希器
     * @param auditService 管理审计服务
     */
    public UserAdministrationServiceImpl(
            AccessMapper mapper,
            OrganizationService organizationService,
            PasswordEncoder passwordEncoder,
            ManagementAuditService auditService
    ) {
        this.mapper = mapper;
        this.organizationService = organizationService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * 查询操作人机构范围内的用户。
     *
     * @param actor 操作人信息
     * @return 用户记录
     */
    @Transactional(readOnly = true)
    @Override
    public List<ManagedUserVO> findAll(AccessActor actor) {
        return mapper.findUsers().stream()
                .filter(user -> actor.canAccess(user.organizationCode()))
                .map(this::enrich)
                .toList();
    }

    /**
     * 查询机构范围内的指定用户。
     *
     * @param userId 用户主键
     * @param actor 操作人信息
     * @return 用户记录
     */
    @Transactional(readOnly = true)
    @Override
    public ManagedUserVO get(long userId, AccessActor actor) {
        ManagedUserSummary user = requireUser(userId);
        requireAccess(actor, user.organizationCode());
        return enrich(user);
    }

    /**
     * 创建归属获批机构且必须首次改密的平台用户。
     *
     * @param command 创建命令
     * @param actor 操作人信息
     * @return 新用户记录
     */
    @Transactional
    @Override
    public ManagedUserVO create(CreateUserCommand command, AccessActor actor) {
        String loginName = normalizeLogin(command.loginName());
        String displayName = requireText(command.displayName(), "displayName", 100);
        validatePassword(command.temporaryPassword(), loginName);
        OrganizationVO organization = requireEnabledOrganization(command.primaryOrganizationId());
        requireAccess(actor, organization.organizationCode());
        if (mapper.userLoginExists(loginName)) {
            throw new ResourceConflictException("登录名已存在");
        }
        CreateUserCommand normalized = new CreateUserCommand(
                loginName, displayName, organization.id(), command.temporaryPassword()
        );
        long userId = mapper.createUser(
                normalized, passwordEncoder.encode(command.temporaryPassword()), actor.loginName()
        );
        mapper.replaceUserOrganizations(userId, List.of(organization.id()), actor.loginName());
        ManagedUserVO created = get(userId, actor);
        audit(actor, created, "USER_CREATED", "创建用户并授予主机构范围");
        return created;
    }

    /**
     * 修改用户显示名称和主机构。
     *
     * @param userId 用户主键
     * @param command 修改命令
     * @param actor 操作人信息
     * @return 修改后用户记录
     */
    @Transactional
    @Override
    public ManagedUserVO update(long userId, UpdateUserCommand command, AccessActor actor) {
        ManagedUserSummary current = requireUser(userId);
        requireAccess(actor, current.organizationCode());
        OrganizationVO organization = requireEnabledOrganization(command.primaryOrganizationId());
        requireAccess(actor, organization.organizationCode());
        requireVersion(command.expectedVersion());
        UpdateUserCommand normalized = new UpdateUserCommand(
                requireText(command.displayName(), "displayName", 100), organization.id(), command.expectedVersion()
        );
        if (mapper.updateUser(userId, normalized, actor.loginName()) != 1) {
            throw new ResourceConflictException("用户资料已被他人修改，请刷新后重试");
        }
        List<Long> scopes = mapper.findUserOrganizationIds(userId);
        if (!scopes.contains(organization.id())) {
            java.util.ArrayList<Long> updatedScopes = new java.util.ArrayList<>(scopes);
            updatedScopes.add(organization.id());
            mapper.replaceUserOrganizations(userId, distinctPositiveIds(updatedScopes), actor.loginName());
        }
        ManagedUserVO updated = get(userId, actor);
        audit(actor, updated, "USER_UPDATED", "修改用户基础信息和主机构");
        return updated;
    }

    /**
     * 恢复或注销用户账号，并保护最后一个平台管理员。
     *
     * @param userId 用户主键
     * @param enabled 目标状态
     * @param expectedVersion 并发版本
     * @param actor 操作人信息
     * @return 修改后用户记录
     */
    @Transactional
    @Override
    public ManagedUserVO setEnabled(long userId, boolean enabled, byte[] expectedVersion, AccessActor actor) {
        ManagedUserSummary current = requireUser(userId);
        requireAccess(actor, current.organizationCode());
        requireVersion(expectedVersion);
        if (!enabled && mapper.isEnabledPlatformAdministrator(userId)
                && mapper.countOtherEnabledPlatformAdministrators(userId) == 0) {
            throw new ResourceConflictException("不能注销最后一个可登录的平台管理员；请先为另一名用户分配管理员角色");
        }
        if (mapper.setUserEnabled(userId, enabled, expectedVersion, actor.loginName()) != 1) {
            throw new ResourceConflictException("用户资料已被他人修改，请刷新后重试");
        }
        ManagedUserVO updated = get(userId, actor);
        audit(actor, updated, enabled ? "USER_ENABLED" : "USER_DISABLED",
                enabled ? "恢复用户账号使用" : "注销用户账号；历史操作记录继续保留");
        return updated;
    }

    /**
     * 为机构范围内用户设置新的临时密码并强制下次登录改密。
     *
     * @param userId 用户主键
     * @param temporaryPassword 新临时密码
     * @param actor 操作人信息
     */
    @Transactional
    @Override
    public void resetPassword(long userId, String temporaryPassword, AccessActor actor) {
        ManagedUserSummary current = requireUser(userId);
        requireAccess(actor, current.organizationCode());
        validatePassword(temporaryPassword, current.loginName());
        if (mapper.resetPassword(
                userId, passwordEncoder.encode(temporaryPassword), actor.loginName()
        ) != 1) {
            throw new ResourceConflictException("密码重置失败，请刷新用户资料后重试");
        }
        audit(actor, current, "USER_PASSWORD_RESET", "重置临时密码并要求下次登录修改；未记录密码");
    }

    /**
     * 原子替换用户角色并保护最后一个平台管理员。
     *
     * @param userId 用户主键
     * @param roleIds 目标角色主键
     * @param actor 操作人信息
     * @return 修改后用户记录
     */
    @Transactional
    @Override
    public ManagedUserVO replaceRoles(long userId, List<Long> roleIds, AccessActor actor) {
        ManagedUserSummary current = requireUser(userId);
        requireAccess(actor, current.organizationCode());
        List<Long> normalized = distinctPositiveIds(roleIds);
        if (!normalized.isEmpty() && mapper.countEnabledRoles(normalized) != normalized.size()) {
            throw new InvalidRequestException("roleIds 中包含不存在或已停用的角色");
        }
        Long platformAdminRoleId = mapper.findRoles().stream()
                .filter(role -> "PLATFORM_ADMIN".equals(role.roleCode()))
                .map(RoleSummary::id)
                .findFirst()
                .orElse(null);
        if (mapper.isEnabledPlatformAdministrator(userId)
                && !normalized.contains(platformAdminRoleId)
                && mapper.countOtherEnabledPlatformAdministrators(userId) == 0) {
            throw new ResourceConflictException("最后一个已启用的平台管理员必须保留管理员角色");
        }
        mapper.replaceUserRoles(userId, normalized, actor.loginName());
        ManagedUserVO updated = get(userId, actor);
        audit(actor, updated, "USER_ROLES_REPLACED", "替换用户角色；角色数量=" + normalized.size());
        return updated;
    }

    /**
     * 原子替换用户机构范围；范围必须包含主机构且不能超出操作人范围。
     *
     * @param userId 用户主键
     * @param organizationIds 目标机构主键
     * @param actor 操作人信息
     * @return 修改后用户记录
     */
    @Transactional
    @Override
    public ManagedUserVO replaceOrganizations(
            long userId,
            List<Long> organizationIds,
            AccessActor actor
    ) {
        ManagedUserSummary current = requireUser(userId);
        requireAccess(actor, current.organizationCode());
        List<Long> normalized = distinctPositiveIds(organizationIds);
        if (!normalized.contains(current.primaryOrganizationId())) {
            throw new InvalidRequestException("可访问机构必须包含用户的主要机构");
        }
        for (Long organizationId : normalized) {
            OrganizationVO organization = requireEnabledOrganization(organizationId);
            requireAccess(actor, organization.organizationCode());
        }
        mapper.replaceUserOrganizations(userId, normalized, actor.loginName());
        ManagedUserVO updated = get(userId, actor);
        audit(actor, updated, "USER_ORGANIZATION_SCOPES_REPLACED",
                "替换用户机构范围；机构数量=" + normalized.size());
        return updated;
    }

    /**
     * 追加不包含DDL全文和连接信息的管理审计事件。
     *
     * @param actor 操作人
     * @param user 用户
     * @param action 动作
     * @param summary 不含敏感内容的摘要
     */
    private void audit(AccessActor actor, ManagedUserVO user, String action, String summary) {
        auditService.recordSuccess(new ManagementAuditCommand(actor, null, user.primaryOrganizationId(),
                user.organizationCode(), action, "USER", user.loginName(), "SUCCESS", summary,
                ManagementAuditServiceImpl.currentRequestId()));
    }

    /**
     * 追加不包含DDL全文和连接信息的管理审计事件。
     *
     * @param actor 操作人
     * @param user 用户
     * @param action 动作
     * @param summary 不含敏感内容的摘要
     */
    private void audit(AccessActor actor, ManagedUserSummary user, String action, String summary) {
        auditService.recordSuccess(new ManagementAuditCommand(actor, null, user.primaryOrganizationId(),
                user.organizationCode(), action, "USER", user.loginName(), "SUCCESS", summary,
                ManagementAuditServiceImpl.currentRequestId()));
    }

    /**
     * 组装用户及关联关系快照。
     *
     * @param user 用户基础快照
     * @return 完整用户记录
     */
    private ManagedUserVO enrich(ManagedUserSummary user) {
        return new ManagedUserVO(
                user.id(), user.loginName(), user.displayName(), user.primaryOrganizationId(),
                user.organizationCode(), user.enabled(), user.mustChangePassword(), user.createdAt(),
                user.updatedAt(), user.version(), mapper.findUserRoleIds(user.id()),
                mapper.findUserOrganizationIds(user.id())
        );
    }

    /**
     * 读取用户；不存在时抛出资源不存在异常。
     *
     * @param userId 用户主键
     * @return 存在的用户
     */
    private ManagedUserSummary requireUser(long userId) {
        if (userId <= 0) {
            throw new InvalidRequestException("userId 必须大于 0");
        }
        return mapper.findUser(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Platform user was not found"));
    }

    /**
     * 校验目标主机构存在且处于启用状态。
     *
     * @param organizationId 机构主键
     * @return 已启用机构
     */
    private OrganizationVO requireEnabledOrganization(long organizationId) {
        OrganizationVO organization = organizationService.get(organizationId);
        if (!organization.enabled()) {
            throw new ResourceConflictException("机构已停用");
        }
        return organization;
    }

    /**
     * 校验当前操作人拥有目标机构数据范围。
     *
     * @param actor 操作人
     * @param organizationCode 机构代码
     */
    private void requireAccess(AccessActor actor, String organizationCode) {
        if (!actor.canAccess(organizationCode)) {
            throw new AccessDeniedException("当前账号无权访问该机构");
        }
    }

    /**
     * 规范化登录名并应用稳定大小写规则。
     *
     * @param value 登录名
     * @return 规范化登录名
     */
    private String normalizeLogin(String value) {
        String loginName = requireText(value, "loginName", 64).toLowerCase(Locale.ROOT);
        if (!LOGIN_PATTERN.matcher(loginName).matches()) {
            throw new InvalidRequestException("loginName 格式无效");
        }
        return loginName;
    }

    /**
     * 校验密码长度及复杂度要求。
     *
     * @param password 密码
     * @param loginName 登录名
     */
    private void validatePassword(String password, String loginName) {
        if (password == null || password.length() < MINIMUM_PASSWORD_LENGTH
                || password.length() > MAXIMUM_PASSWORD_LENGTH) {
            throw new InvalidRequestException("密码长度不符合要求");
        }
        if (password.toLowerCase(Locale.ROOT).contains(loginName.toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestException("密码不能包含登录名");
        }
    }

    /**
     * 校验用户必填文本并返回裁剪后的值。
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
     * 去重并校验关系主键均为正数。
     *
     * @param ids 原始主键
     * @return 去重后的正数主键
     */
    private List<Long> distinctPositiveIds(List<Long> ids) {
        if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new InvalidRequestException("编号列表中包含无效值");
        }
        return ids.stream().distinct().sorted().toList();
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
