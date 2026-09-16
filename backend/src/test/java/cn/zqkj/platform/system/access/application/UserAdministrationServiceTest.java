package cn.zqkj.platform.system.access.application;

import cn.zqkj.platform.foundation.web.error.InvalidRequestException;
import cn.zqkj.platform.foundation.web.error.ResourceConflictException;
import cn.zqkj.platform.modules.organization.application.OrganizationService;
import cn.zqkj.platform.modules.organization.application.OrganizationView;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证用户管理的机构范围、最后管理员和关联关系规则。
 */
class UserAdministrationServiceTest {

    /** 验证跨机构用户详情被服务端拒绝。 */
    @Test
    void rejectsUserOutsideActorScope() {
        AccessRepository repository = mock(AccessRepository.class);
        when(repository.findUser(2L)).thenReturn(Optional.of(user(2L, "ORG002", true)));
        UserAdministrationService service = service(repository, mock(OrganizationService.class));

        assertThrows(AccessDeniedException.class, () -> service.get(2L, actor()));
    }

    /** 验证不能停用最后一个启用的平台管理员。 */
    @Test
    void protectsLastPlatformAdministrator() {
        AccessRepository repository = mock(AccessRepository.class);
        when(repository.findUser(1L)).thenReturn(Optional.of(user(1L, "ORG001", true)));
        when(repository.isEnabledPlatformAdministrator(1L)).thenReturn(true);
        when(repository.countOtherEnabledPlatformAdministrators(1L)).thenReturn(0);
        UserAdministrationService service = service(repository, mock(OrganizationService.class));

        assertThrows(ResourceConflictException.class,
                () -> service.setEnabled(1L, false, version(), actor()));
        verify(repository, never()).setUserEnabled(anyLong(), eq(false), any(), any());
    }

    /** 验证创建用户时只授予其主机构范围并强制使用哈希密码。 */
    @Test
    void createsUserWithPrimaryOrganizationScope() {
        AccessRepository repository = mock(AccessRepository.class);
        OrganizationService organizationService = mock(OrganizationService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(organizationService.get(10L)).thenReturn(organization(10L, "ORG001"));
        when(encoder.encode("Temporary!123")).thenReturn("hash");
        when(repository.createUser(any(), eq("hash"), eq("admin"))).thenReturn(2L);
        when(repository.findUser(2L)).thenReturn(Optional.of(user(2L, "ORG001", true)));
        when(repository.findUserRoleIds(2L)).thenReturn(List.of());
        when(repository.findUserOrganizationIds(2L)).thenReturn(List.of(10L));
        UserAdministrationService service = new UserAdministrationService(repository, organizationService, encoder);

        ManagedUserView result = service.create(
                new CreateUserCommand("Operator", "Operator", 10L, "Temporary!123"), actor()
        );

        assertEquals("operator", result.loginName());
        verify(repository).replaceUserOrganizations(2L, List.of(10L), "admin");
    }

    /** 验证机构范围不能排除用户主机构。 */
    @Test
    void requiresPrimaryOrganizationInScope() {
        AccessRepository repository = mock(AccessRepository.class);
        when(repository.findUser(2L)).thenReturn(Optional.of(user(2L, "ORG001", true)));
        UserAdministrationService service = service(repository, mock(OrganizationService.class));

        assertThrows(InvalidRequestException.class,
                () -> service.replaceOrganizations(2L, List.of(20L), actor()));
    }

    /** 验证最后一个平台管理员不能移除自身平台管理员角色。 */
    @Test
    void protectsLastPlatformAdministratorRole() {
        AccessRepository repository = mock(AccessRepository.class);
        when(repository.findUser(1L)).thenReturn(Optional.of(user(1L, "ORG001", true)));
        when(repository.findRoles()).thenReturn(List.of(new RoleSummary(
                9L, "PLATFORM_ADMIN", "Platform Administrator", true, true,
                LocalDateTime.now(), LocalDateTime.now(), version()
        )));
        when(repository.isEnabledPlatformAdministrator(1L)).thenReturn(true);
        when(repository.countOtherEnabledPlatformAdministrators(1L)).thenReturn(0);
        UserAdministrationService service = service(repository, mock(OrganizationService.class));

        assertThrows(ResourceConflictException.class,
                () -> service.replaceRoles(1L, List.of(), actor()));
        verify(repository, never()).replaceUserRoles(anyLong(), any(), any());
    }

    /** @return 固定操作人 */
    private AccessActor actor() {
        return new AccessActor(1L, "admin", Set.of("ORG001"));
    }

    /** @param repository 仓储 @param organizationService 机构服务 @return 用户服务 */
    private UserAdministrationService service(
            AccessRepository repository,
            OrganizationService organizationService
    ) {
        return new UserAdministrationService(repository, organizationService, mock(PasswordEncoder.class));
    }

    /** @param id 用户主键 @param organizationCode 机构代码 @param enabled 状态 @return 用户快照 */
    private ManagedUserSummary user(long id, String organizationCode, boolean enabled) {
        return new ManagedUserSummary(
                id, id == 1L ? "admin" : "operator", "User", 10L, organizationCode,
                enabled, false, LocalDateTime.now(), LocalDateTime.now(), version()
        );
    }

    /** @param id 机构主键 @param code 机构代码 @return 机构快照 */
    private OrganizationView organization(long id, String code) {
        return new OrganizationView(
                id, code, "Organization", "HOSPITAL", null, true,
                null, null, LocalDateTime.now(), LocalDateTime.now(), version()
        );
    }

    /** @return 固定并发版本 */
    private byte[] version() {
        return new byte[Long.BYTES];
    }
}
