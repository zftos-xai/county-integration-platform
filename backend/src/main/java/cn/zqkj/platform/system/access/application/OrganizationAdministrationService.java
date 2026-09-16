package cn.zqkj.platform.system.access.application;

import cn.zqkj.platform.foundation.web.error.InvalidRequestException;
import cn.zqkj.platform.foundation.web.error.ResourceConflictException;
import cn.zqkj.platform.modules.organization.application.CreateOrganizationCommand;
import cn.zqkj.platform.modules.organization.application.OrganizationService;
import cn.zqkj.platform.modules.organization.application.OrganizationView;
import cn.zqkj.platform.modules.organization.application.UpdateOrganizationCommand;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 协调机构唯一写入服务与用户机构范围，落实机构管理的数据范围边界。
 */
@Service
public class OrganizationAdministrationService {

    private final OrganizationService organizationService;
    private final AccessRepository accessRepository;

    /**
     * 创建机构授权协调服务。
     *
     * @param organizationService 机构唯一写入服务
     * @param accessRepository 机构范围持久化边界
     */
    public OrganizationAdministrationService(
            OrganizationService organizationService,
            AccessRepository accessRepository
    ) {
        this.organizationService = organizationService;
        this.accessRepository = accessRepository;
    }

    /** @param enabled 可选启用状态 @param actor 操作人 @return 范围内机构 */
    @Transactional(readOnly = true)
    public List<OrganizationView> findAll(Boolean enabled, AccessActor actor) {
        return organizationService.findAll(enabled).stream()
                .filter(organization -> actor.canAccess(organization.organizationCode()))
                .toList();
    }

    /** @param organizationId 机构主键 @param actor 操作人 @return 范围内机构 */
    @Transactional(readOnly = true)
    public OrganizationView get(long organizationId, AccessActor actor) {
        OrganizationView organization = organizationService.get(organizationId);
        requireAccess(actor, organization.organizationCode());
        return organization;
    }

    /**
     * 在操作人可访问父机构下创建子机构并授予创建人显式机构范围。
     *
     * @param command 创建命令
     * @param actor 操作人
     * @return 新机构
     */
    @Transactional
    public OrganizationView create(CreateOrganizationCommand command, AccessActor actor) {
        if (command.parentId() == null) {
            throw new InvalidRequestException("A managed organization must have an accessible parent organization");
        }
        get(command.parentId(), actor);
        OrganizationView created = organizationService.create(command, actor.loginName());
        List<Long> scopes = new ArrayList<>(accessRepository.findUserOrganizationIds(actor.userId()));
        scopes.add(created.id());
        accessRepository.replaceUserOrganizations(
                actor.userId(), scopes.stream().distinct().sorted().toList(), actor.loginName()
        );
        return created;
    }

    /**
     * 修改范围内机构并要求新父机构也在操作人范围内。
     *
     * @param organizationId 机构主键
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改后机构
     */
    @Transactional
    public OrganizationView update(long organizationId, UpdateOrganizationCommand command, AccessActor actor) {
        get(organizationId, actor);
        if (command.parentId() != null) {
            get(command.parentId(), actor);
        }
        return organizationService.update(organizationId, command, actor.loginName());
    }

    /**
     * 修改范围内机构启用状态。
     *
     * @param organizationId 机构主键
     * @param enabled 目标状态
     * @param expectedVersion 并发版本
     * @param actor 操作人
     * @return 修改后机构
     */
    @Transactional
    public OrganizationView setEnabled(
            long organizationId,
            boolean enabled,
            byte[] expectedVersion,
            AccessActor actor
    ) {
        get(organizationId, actor);
        if (!enabled && accessRepository.hasEnabledPrimaryUsers(organizationId)) {
            throw new ResourceConflictException("Organization has enabled primary users");
        }
        return organizationService.setEnabled(
                organizationId, enabled, expectedVersion, actor.loginName()
        );
    }

    /** @param actor 操作人 @param organizationCode 机构代码 */
    private void requireAccess(AccessActor actor, String organizationCode) {
        if (!actor.canAccess(organizationCode)) {
            throw new AccessDeniedException("Organization access is not permitted");
        }
    }
}
