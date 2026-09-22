package cn.zqkj.platform.system.organization.service;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.identity.mapper.AccessMapper;
import cn.zqkj.platform.system.organization.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.organization.service.impl.OrganizationAdministrationServiceImpl;
import java.time.LocalDateTime;
import java.util.Optional;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import cn.zqkj.platform.system.identity.domain.model.ManagedUserSummary;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证机构管理与显式机构范围的协调规则。
 */
class OrganizationAdministrationServiceTest {

    /** 机构列表把授权范围交给数据库查询，而不是读取全表后再过滤。 */
    @Test
    void delegatesVisibleOrganizationScopeToQuery() {
        OrganizationService organizations = mock(OrganizationService.class);
        OrganizationAdministrationService service = new OrganizationAdministrationServiceImpl(
                organizations, mock(AccessMapper.class), mock(IdentityMapper.class), mock(ManagementAuditService.class));

        service.findAll(true, actor());

        verify(organizations).findVisible(true, List.of("ORG001"));
    }

    /** 验证普通管理入口不能创建没有父机构的新根机构。 */
    @Test
    void rejectsAdditionalRootOrganization() {
        OrganizationAdministrationService service = new OrganizationAdministrationServiceImpl(
                mock(OrganizationService.class), mock(AccessMapper.class), mock(IdentityMapper.class), mock(ManagementAuditService.class)
        );

        assertThrows(InvalidRequestException.class, () -> service.create(
                new CreateOrganizationCommand("ORG002", "Child", "CLINIC", null, null, null), actor()
        ));
    }

    /** 验证新建子机构后自动加入创建人的显式机构范围。 */
    @Test
    void grantsCreatedOrganizationToActor() {
        OrganizationService organizationService = mock(OrganizationService.class);
        AccessMapper mapper = mock(AccessMapper.class);
        ManagementAuditService auditService = mock(ManagementAuditService.class);
        when(organizationService.getVisible(10L, List.of("ORG001"))).thenReturn(organization(10L, "ORG001", null));
        when(organizationService.create(any(), eq("admin"))).thenReturn(organization(20L, "ORG002", 10L));
        IdentityMapper identityMapper = mock(IdentityMapper.class);
        when(mapper.findUser(1L, List.of("ORG001"))).thenReturn(Optional.of(mock(ManagedUserSummary.class)));
        OrganizationAdministrationService service = new OrganizationAdministrationServiceImpl(
                organizationService, mapper, identityMapper, auditService
        );

        service.create(new CreateOrganizationCommand(
                "ORG002", "Child", "CLINIC", 10L, null, null
        ), actor());

        verify(identityMapper).grantOrganization(1L, 20L, "admin");
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).findUserOrganizationIds(1L);
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).replaceUserOrganizations(org.mockito.ArgumentMatchers.anyLong(), any(), any());
        ArgumentCaptor<ManagementAuditCommand> captor = ArgumentCaptor.forClass(ManagementAuditCommand.class);
        verify(auditService).append(captor.capture());
        assertEquals("ORGANIZATION_CREATED", captor.getValue().actionCode());
        assertEquals("ORG002", captor.getValue().targetId());
    }

    /** 验证存在启用主归属用户时不能停用机构。 */
    @Test
    void rejectsDisableWithEnabledPrimaryUser() {
        OrganizationService organizationService = mock(OrganizationService.class);
        AccessMapper mapper = mock(AccessMapper.class);
        when(organizationService.getVisible(10L, List.of("ORG001"))).thenReturn(organization(10L, "ORG001", null));
        when(mapper.hasEnabledPrimaryUsers(10L)).thenReturn(true);
        OrganizationAdministrationService service = new OrganizationAdministrationServiceImpl(
                organizationService, mapper, mock(IdentityMapper.class), mock(ManagementAuditService.class)
        );

        assertThrows(ResourceConflictException.class,
                () -> service.setEnabled(10L, false, new byte[Long.BYTES], actor()));
    }

    /**
     * 创建具有测试机构范围的服务层操作人。
     *
     * @return 固定操作人
     */
    private AccessActor actor() {
        return new AccessActor(1L, "admin", Set.of("ORG001"));
    }

    /**
     * 创建机构测试快照。
     *
     * @param id 主键
     * @param code 编码
     * @param parentId 父机构
     * @return 机构记录
     */
    private OrganizationVO organization(long id, String code, Long parentId) {
        return new OrganizationVO(
                id, code, "Organization", "HOSPITAL", parentId, true,
                null, null, LocalDateTime.now(), LocalDateTime.now(), new byte[Long.BYTES]
        );
    }
}
