package cn.zqkj.platform.system.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.system.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.domain.dto.UpdateOrganizationCommand;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.mapper.AccessMapper;
import cn.zqkj.platform.system.service.ManagementAuditService;
import cn.zqkj.platform.system.service.OrganizationAdministrationService;
import cn.zqkj.platform.system.service.OrganizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 协调机构唯一写入服务与用户机构范围，落实机构管理的数据范围边界。
 */
@Service
public class OrganizationAdministrationServiceImpl implements OrganizationAdministrationService {

    private final OrganizationService organizationService;
    private final AccessMapper accessMapper;
    private final ManagementAuditService auditService;

    /**
     * 创建机构授权协调服务。
     *
     * @param organizationService 机构唯一写入服务
     * @param accessMapper 机构范围持久化边界
     * @param auditService 管理审计服务
     */
    public OrganizationAdministrationServiceImpl(
            OrganizationService organizationService,
            AccessMapper accessMapper,
            ManagementAuditService auditService
    ) {
        this.organizationService = organizationService;
        this.accessMapper = accessMapper;
        this.auditService = auditService;
    }

    /**
     * 查询当前操作人机构范围内的机构档案。
     *
     * @param enabled 可选启用状态
     * @param actor 操作人
     * @return 范围内机构
     */
    @Transactional(readOnly = true)
    @Override
    public List<OrganizationVO> findAll(Boolean enabled, AccessActor actor) {
        return organizationService.findAll(enabled).stream()
                .filter(organization -> actor.canAccess(organization.organizationCode()))
                .toList();
    }

    /**
     * 读取当前操作人有权访问的机构档案。
     *
     * @param organizationId 机构主键
     * @param actor 操作人
     * @return 范围内机构
     */
    @Transactional(readOnly = true)
    @Override
    public OrganizationVO get(long organizationId, AccessActor actor) {
        OrganizationVO organization = organizationService.get(organizationId);
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
    @Override
    public OrganizationVO create(CreateOrganizationCommand command, AccessActor actor) {
        if (command.parentId() == null) {
            throw new InvalidRequestException("新增下级机构时，上级机构必须在当前账号可访问范围内");
        }
        get(command.parentId(), actor);
        OrganizationVO created = organizationService.create(command, actor.loginName());
        List<Long> scopes = new ArrayList<>(accessMapper.findUserOrganizationIds(actor.userId()));
        scopes.add(created.id());
        accessMapper.replaceUserOrganizations(
                actor.userId(), scopes.stream().distinct().sorted().toList(), actor.loginName()
        );
        audit(actor, created, "ORGANIZATION_CREATED", "创建机构并授予创建人机构范围");
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
    @Override
    public OrganizationVO update(long organizationId, UpdateOrganizationCommand command, AccessActor actor) {
        get(organizationId, actor);
        if (command.parentId() != null) {
            get(command.parentId(), actor);
        }
        OrganizationVO updated = organizationService.update(organizationId, command, actor.loginName());
        audit(actor, updated, "ORGANIZATION_UPDATED", "修改机构基础信息");
        return updated;
    }

    /**
     * 恢复或撤销范围内机构。
     *
     * @param organizationId 机构主键
     * @param enabled 目标状态
     * @param expectedVersion 并发版本
     * @param actor 操作人
     * @return 修改后机构
     */
    @Transactional
    @Override
    public OrganizationVO setEnabled(
            long organizationId,
            boolean enabled,
            byte[] expectedVersion,
            AccessActor actor
    ) {
        get(organizationId, actor);
        if (!enabled && accessMapper.hasEnabledPrimaryUsers(organizationId)) {
            throw new ResourceConflictException("该机构仍有以此为主要机构的可登录用户，不能撤销；请先调整这些用户的主要机构或注销账号");
        }
        OrganizationVO updated = organizationService.setEnabled(
                organizationId, enabled, expectedVersion, actor.loginName()
        );
        audit(actor, updated, enabled ? "ORGANIZATION_ENABLED" : "ORGANIZATION_DISABLED",
                enabled ? "恢复使用机构" : "撤销机构；历史数据和操作记录继续保留");
        return updated;
    }

    /**
     * 追加不包含DDL全文和连接信息的管理审计事件。
     *
     * @param actor 操作人
     * @param organization 机构
     * @param action 动作
     * @param summary 不含敏感内容的摘要
     */
    private void audit(AccessActor actor, OrganizationVO organization, String action, String summary) {
        auditService.recordSuccess(new ManagementAuditCommand(actor, null, organization.id(),
                organization.organizationCode(), action, "ORGANIZATION", organization.organizationCode(),
                "SUCCESS", summary, ManagementAuditServiceImpl.currentRequestId()));
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
}
