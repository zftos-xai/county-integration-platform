package cn.zqkj.platform.system.service;
import cn.zqkj.platform.system.service.impl.OrganizationAdministrationServiceImpl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.system.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.mapper.AccessMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证机构管理与显式机构范围的协调规则。
 */
class OrganizationAdministrationServiceTest {

    /** 验证普通管理入口不能创建没有父机构的新根机构。 */
    @Test
    void rejectsAdditionalRootOrganization() {
        OrganizationAdministrationService service = new OrganizationAdministrationServiceImpl(
                mock(OrganizationService.class), mock(AccessMapper.class), mock(ManagementAuditService.class)
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
        when(organizationService.get(10L)).thenReturn(organization(10L, "ORG001", null));
        when(organizationService.create(any(), eq("admin"))).thenReturn(organization(20L, "ORG002", 10L));
        when(mapper.findUserOrganizationIds(1L)).thenReturn(List.of(10L));
        OrganizationAdministrationService service = new OrganizationAdministrationServiceImpl(
                organizationService, mapper, auditService
        );

        service.create(new CreateOrganizationCommand(
                "ORG002", "Child", "CLINIC", 10L, null, null
        ), actor());

        verify(mapper).replaceUserOrganizations(1L, List.of(10L, 20L), "admin");
        ArgumentCaptor<ManagementAuditCommand> captor = ArgumentCaptor.forClass(ManagementAuditCommand.class);
        verify(auditService).recordSuccess(captor.capture());
        assertEquals("ORGANIZATION_CREATED", captor.getValue().actionCode());
        assertEquals("ORG002", captor.getValue().targetId());
    }

    /** 验证存在启用主归属用户时不能停用机构。 */
    @Test
    void rejectsDisableWithEnabledPrimaryUser() {
        OrganizationService organizationService = mock(OrganizationService.class);
        AccessMapper mapper = mock(AccessMapper.class);
        when(organizationService.get(10L)).thenReturn(organization(10L, "ORG001", null));
        when(mapper.hasEnabledPrimaryUsers(10L)).thenReturn(true);
        OrganizationAdministrationService service = new OrganizationAdministrationServiceImpl(
                organizationService, mapper, mock(ManagementAuditService.class)
        );

        assertThrows(ResourceConflictException.class,
                () -> service.setEnabled(10L, false, new byte[Long.BYTES], actor()));
    }

    /** @return 固定操作人 */
    private AccessActor actor() {
        return new AccessActor(1L, "admin", Set.of("ORG001"));
    }

    /** @param id 主键 @param code 编码 @param parentId 父机构 @return 机构快照 */
    private OrganizationVO organization(long id, String code, Long parentId) {
        return new OrganizationVO(
                id, code, "Organization", "HOSPITAL", parentId, true,
                null, null, LocalDateTime.now(), LocalDateTime.now(), new byte[Long.BYTES]
        );
    }
}
