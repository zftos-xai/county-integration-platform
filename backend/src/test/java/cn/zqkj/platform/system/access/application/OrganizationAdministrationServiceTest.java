package cn.zqkj.platform.system.access.application;

import cn.zqkj.platform.foundation.web.error.InvalidRequestException;
import cn.zqkj.platform.foundation.web.error.ResourceConflictException;
import cn.zqkj.platform.modules.organization.application.CreateOrganizationCommand;
import cn.zqkj.platform.modules.organization.application.OrganizationService;
import cn.zqkj.platform.modules.organization.application.OrganizationView;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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

    /** 验证普通管理入口不能创建没有父机构的新根机构。 */
    @Test
    void rejectsAdditionalRootOrganization() {
        OrganizationAdministrationService service = new OrganizationAdministrationService(
                mock(OrganizationService.class), mock(AccessRepository.class)
        );

        assertThrows(InvalidRequestException.class, () -> service.create(
                new CreateOrganizationCommand("ORG002", "Child", "CLINIC", null, null, null), actor()
        ));
    }

    /** 验证新建子机构后自动加入创建人的显式机构范围。 */
    @Test
    void grantsCreatedOrganizationToActor() {
        OrganizationService organizationService = mock(OrganizationService.class);
        AccessRepository repository = mock(AccessRepository.class);
        when(organizationService.get(10L)).thenReturn(organization(10L, "ORG001", null));
        when(organizationService.create(any(), eq("admin"))).thenReturn(organization(20L, "ORG002", 10L));
        when(repository.findUserOrganizationIds(1L)).thenReturn(List.of(10L));
        OrganizationAdministrationService service = new OrganizationAdministrationService(
                organizationService, repository
        );

        service.create(new CreateOrganizationCommand(
                "ORG002", "Child", "CLINIC", 10L, null, null
        ), actor());

        verify(repository).replaceUserOrganizations(1L, List.of(10L, 20L), "admin");
    }

    /** 验证存在启用主归属用户时不能停用机构。 */
    @Test
    void rejectsDisableWithEnabledPrimaryUser() {
        OrganizationService organizationService = mock(OrganizationService.class);
        AccessRepository repository = mock(AccessRepository.class);
        when(organizationService.get(10L)).thenReturn(organization(10L, "ORG001", null));
        when(repository.hasEnabledPrimaryUsers(10L)).thenReturn(true);
        OrganizationAdministrationService service = new OrganizationAdministrationService(
                organizationService, repository
        );

        assertThrows(ResourceConflictException.class,
                () -> service.setEnabled(10L, false, new byte[Long.BYTES], actor()));
    }

    /** @return 固定操作人 */
    private AccessActor actor() {
        return new AccessActor(1L, "admin", Set.of("ORG001"));
    }

    /** @param id 主键 @param code 编码 @param parentId 父机构 @return 机构快照 */
    private OrganizationView organization(long id, String code, Long parentId) {
        return new OrganizationView(
                id, code, "Organization", "HOSPITAL", parentId, true,
                null, null, LocalDateTime.now(), LocalDateTime.now(), new byte[Long.BYTES]
        );
    }
}
