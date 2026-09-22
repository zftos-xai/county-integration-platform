package cn.zqkj.platform.system.identity.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.exception.ResourceNotFoundException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import cn.zqkj.platform.system.identity.domain.dto.CreateUserCommand;
import cn.zqkj.platform.system.identity.domain.dto.UpdateUserCommand;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.identity.domain.model.ManagedUserSummary;
import cn.zqkj.platform.system.identity.domain.model.RoleSummary;
import cn.zqkj.platform.system.identity.domain.model.UserOrganizationAssignment;
import cn.zqkj.platform.system.identity.domain.model.UserRoleAssignment;
import cn.zqkj.platform.system.identity.domain.vo.ManagedUserVO;
import cn.zqkj.platform.system.identity.mapper.AccessMapper;
import cn.zqkj.platform.system.identity.service.UserAdministrationService;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.organization.service.OrganizationService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实现平台用户生命周期、角色分配和机构数据范围管理。
 */
@Service
public class UserAdministrationServiceImpl implements UserAdministrationService {

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
        List<String> organizationCodes = List.copyOf(actor.organizationCodes());
        List<ManagedUserSummary> users = mapper.findUsers(organizationCodes);
        if (users.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> rolesByUser = new LinkedHashMap<>();
        Map<Long, List<Long>> organizationsByUser = new LinkedHashMap<>();
        for (ManagedUserSummary user : users) {
            rolesByUser.put(user.id(), new ArrayList<>());
            organizationsByUser.put(user.id(), new ArrayList<>());
        }
        for (UserRoleAssignment assignment : mapper.findUserRoles(organizationCodes)) {
            List<Long> roleIds = rolesByUser.get(assignment.userId());
            if (roleIds != null) {
                roleIds.add(assignment.roleId());
            }
        }
        for (UserOrganizationAssignment assignment : mapper.findUserOrganizations(organizationCodes)) {
            List<Long> organizationIds = organizationsByUser.get(assignment.userId());
            if (organizationIds != null) {
                organizationIds.add(assignment.organizationId());
            }
        }
        List<ManagedUserVO> result = new ArrayList<>(users.size());
        for (ManagedUserSummary user : users) {
            result.add(toView(user, rolesByUser.get(user.id()), organizationsByUser.get(user.id())));
        }
        return List.copyOf(result);
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
        ManagedUserSummary user = requireUser(userId, List.copyOf(actor.organizationCodes()));
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
        String loginName = command.loginName();
        rejectPasswordContainingLogin(command.temporaryPassword(), loginName);
        OrganizationVO organization = requireEnabledOrganization(command.primaryOrganizationId(), List.copyOf(actor.organizationCodes()));
        if (mapper.userLoginExists(loginName)) {
            throw new ResourceConflictException("登录名已存在");
        }
        long userId = mapper.createUser(
                command, passwordEncoder.encode(command.temporaryPassword()), actor.loginName()
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
        ManagedUserSummary current = requireUser(userId, List.copyOf(actor.organizationCodes()));
        OrganizationVO organization = requireEnabledOrganization(command.primaryOrganizationId(), List.copyOf(actor.organizationCodes()));
        if (mapper.updateUser(userId, command, actor.loginName()) != 1) {
            throw new ResourceConflictException("用户资料已被他人修改，请刷新后重试");
        }
        List<Long> scopes = mapper.findUserOrganizationIds(userId);
        if (!scopes.contains(organization.id())) {
            ArrayList<Long> updatedScopes = new ArrayList<>(scopes);
            updatedScopes.add(organization.id());
            mapper.replaceUserOrganizations(userId, Func.distinct(updatedScopes), actor.loginName());
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
        ManagedUserSummary current = requireUser(userId, List.copyOf(actor.organizationCodes()));
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
        ManagedUserSummary current = requireUser(userId, List.copyOf(actor.organizationCodes()));
        rejectPasswordContainingLogin(temporaryPassword, current.loginName());
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
        ManagedUserSummary current = requireUser(userId, List.copyOf(actor.organizationCodes()));
        List<Long> normalized = Func.distinct(roleIds);
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
        ManagedUserSummary current = requireUser(userId, List.copyOf(actor.organizationCodes()));
        List<Long> normalized = Func.distinct(organizationIds);
        if (!normalized.contains(current.primaryOrganizationId())) {
            throw new InvalidRequestException("可访问机构必须包含用户的主要机构");
        }
        for (Long organizationId : normalized) {
            requireEnabledOrganization(organizationId, List.copyOf(actor.organizationCodes()));
        }
        mapper.replaceUserOrganizations(userId, normalized, actor.loginName());
        ManagedUserVO updated = get(userId, actor);
        audit(actor, updated, "USER_ORGANIZATION_SCOPES_REPLACED",
                "替换用户机构范围；机构数量=" + normalized.size());
        return updated;
    }

    /**
     * 追加账号维护审计，不记录密码或认证凭证。
     *
     * @param actor 操作人
     * @param user 用户
     * @param action 动作
     * @param summary 不含敏感内容的摘要
     */
    private void audit(AccessActor actor, ManagedUserVO user, String action, String summary) {
        auditService.append(new ManagementAuditCommand(actor.userId(), actor.loginName(), user.primaryOrganizationId(),
                user.organizationCode(), action, "USER", user.loginName(), "SUCCESS", summary,
                auditService.currentRequestId()));
    }

    /**
     * 追加账号维护审计，不记录密码或认证凭证。
     *
     * @param actor 操作人
     * @param user 用户
     * @param action 动作
     * @param summary 不含敏感内容的摘要
     */
    private void audit(AccessActor actor, ManagedUserSummary user, String action, String summary) {
        auditService.append(new ManagementAuditCommand(actor.userId(), actor.loginName(), user.primaryOrganizationId(),
                user.organizationCode(), action, "USER", user.loginName(), "SUCCESS", summary,
                auditService.currentRequestId()));
    }

    /**
     * 组装用户及关联关系快照。
     *
     * @param user 用户基础快照
     * @return 完整用户记录
     */
    private ManagedUserVO enrich(ManagedUserSummary user) {
        return toView(user, mapper.findUserRoleIds(user.id()), mapper.findUserOrganizationIds(user.id()));
    }

    /**
     * 使用已批量读取的授权关系组装用户视图。
     *
     * @param user 用户基础快照
     * @param roleIds 用户获授的角色主键
     * @param organizationIds 用户显式可访问的机构主键
     * @return 不含密码哈希的管理端用户视图
     */
    private ManagedUserVO toView(ManagedUserSummary user, List<Long> roleIds, List<Long> organizationIds) {
        return new ManagedUserVO(
                user.id(), user.loginName(), user.displayName(), user.primaryOrganizationId(),
                user.organizationCode(), user.enabled(), user.mustChangePassword(), user.createdAt(),
                user.updatedAt(), user.version(), roleIds, organizationIds
        );
    }

    /**
     * 读取用户；不存在时抛出资源不存在异常。
     *
     * @param userId 用户主键
     * @return 存在的用户
     */
    private ManagedUserSummary requireUser(long userId, List<String> organizationCodes) {
        return mapper.findUser(userId, organizationCodes)
                .orElseThrow(() -> new ResourceNotFoundException("Platform user was not found"));
    }

    /**
     * 校验目标主机构存在且处于启用状态。
     *
     * @param organizationId 机构主键
     * @return 已启用机构
     */
    private OrganizationVO requireEnabledOrganization(long organizationId, List<String> organizationCodes) {
        OrganizationVO organization = organizationService.getVisible(organizationId, organizationCodes);
        if (!organization.enabled()) {
            throw new ResourceConflictException("机构已停用");
        }
        return organization;
    }


    /**
     * 禁止密码包含登录名，长度和必填性由入口校验。
     *
     * @param password 密码
     * @param loginName 登录名
     */
    private void rejectPasswordContainingLogin(String password, String loginName) {
        if (password.toLowerCase(Locale.ROOT).contains(loginName.toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestException("密码不能包含登录名");
        }
    }

}
