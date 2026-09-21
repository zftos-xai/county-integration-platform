package cn.zqkj.platform.system.identity.service;
import cn.zqkj.platform.system.identity.service.impl.UserAdministrationServiceImpl;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import cn.zqkj.platform.system.organization.service.OrganizationService;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.identity.domain.dto.CreateUserCommand;
import cn.zqkj.platform.system.identity.domain.model.ManagedUserSummary;
import cn.zqkj.platform.system.identity.domain.vo.ManagedUserVO;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.identity.domain.model.RoleSummary;
import cn.zqkj.platform.system.identity.mapper.AccessMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        AccessMapper mapper = mock(AccessMapper.class);
        when(mapper.findUser(2L)).thenReturn(java.util.Optional.of(user(2L, "ORG002", true)));
        UserAdministrationService service = service(mapper, mock(OrganizationService.class));

        assertThrows(AccessDeniedException.class, () -> service.get(2L, actor()));
    }

    /** 验证不能停用最后一个启用的平台管理员。 */
    @Test
    void protectsLastPlatformAdministrator() {
        AccessMapper mapper = mock(AccessMapper.class);
        when(mapper.findUser(1L)).thenReturn(java.util.Optional.of(user(1L, "ORG001", true)));
        when(mapper.isEnabledPlatformAdministrator(1L)).thenReturn(true);
        when(mapper.countOtherEnabledPlatformAdministrators(1L)).thenReturn(0);
        UserAdministrationService service = service(mapper, mock(OrganizationService.class));

        assertThrows(ResourceConflictException.class,
                () -> service.setEnabled(1L, false, version(), actor()));
        verify(mapper, never()).setUserEnabled(anyLong(), eq(false), any(), any());
    }

    /** 验证创建用户时只授予其主机构范围并强制使用哈希密码。 */
    @Test
    void createsUserWithPrimaryOrganizationScope() {
        AccessMapper mapper = mock(AccessMapper.class);
        OrganizationService organizationService = mock(OrganizationService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(organizationService.get(10L)).thenReturn(organization(10L, "ORG001"));
        when(encoder.encode("Temporary!123")).thenReturn("hash");
        when(mapper.createUser(any(), eq("hash"), eq("admin"))).thenReturn(2L);
        when(mapper.findUser(2L)).thenReturn(java.util.Optional.of(user(2L, "ORG001", true)));
        when(mapper.findUserRoleIds(2L)).thenReturn(List.of());
        when(mapper.findUserOrganizationIds(2L)).thenReturn(List.of(10L));
        UserAdministrationService service = new UserAdministrationServiceImpl(
                mapper, organizationService, encoder, mock(ManagementAuditService.class)
        );

        ManagedUserVO result = service.create(
                new CreateUserCommand("Operator", "Operator", 10L, "Temporary!123"), actor()
        );

        assertEquals("operator", result.loginName());
        verify(mapper).replaceUserOrganizations(2L, List.of(10L), "admin");
    }

    /** 验证密码重置审计不包含临时密码。 */
    @Test
    void auditsPasswordResetWithoutTemporaryPassword() {
        AccessMapper mapper = mock(AccessMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        ManagementAuditService auditService = mock(ManagementAuditService.class);
        String temporaryPassword = "Temporary!456";
        when(mapper.findUser(2L)).thenReturn(Optional.of(user(2L, "ORG001", true)));
        when(encoder.encode(temporaryPassword)).thenReturn("password-hash");
        when(mapper.resetPassword(2L, "password-hash", "admin")).thenReturn(1);
        UserAdministrationService service = new UserAdministrationServiceImpl(
                mapper, mock(OrganizationService.class), encoder, auditService
        );

        service.resetPassword(2L, temporaryPassword, actor());

        ArgumentCaptor<ManagementAuditCommand> captor = ArgumentCaptor.forClass(ManagementAuditCommand.class);
        verify(auditService).recordSuccess(captor.capture());
        assertEquals("USER_PASSWORD_RESET", captor.getValue().actionCode());
        assertFalse(captor.getValue().changeSummary().contains(temporaryPassword));
        assertFalse(captor.getValue().changeSummary().contains("password-hash"));
    }

    /** 验证机构范围不能排除用户主机构。 */
    @Test
    void requiresPrimaryOrganizationInScope() {
        AccessMapper mapper = mock(AccessMapper.class);
        when(mapper.findUser(2L)).thenReturn(java.util.Optional.of(user(2L, "ORG001", true)));
        UserAdministrationService service = service(mapper, mock(OrganizationService.class));

        assertThrows(InvalidRequestException.class,
                () -> service.replaceOrganizations(2L, List.of(20L), actor()));
    }

    /** 验证最后一个平台管理员不能移除自身平台管理员角色。 */
    @Test
    void protectsLastPlatformAdministratorRole() {
        AccessMapper mapper = mock(AccessMapper.class);
        when(mapper.findUser(1L)).thenReturn(java.util.Optional.of(user(1L, "ORG001", true)));
        when(mapper.findRoles()).thenReturn(List.of(new RoleSummary(
                9L, "PLATFORM_ADMIN", "Platform Administrator", true, true,
                LocalDateTime.now(), LocalDateTime.now(), version()
        )));
        when(mapper.isEnabledPlatformAdministrator(1L)).thenReturn(true);
        when(mapper.countOtherEnabledPlatformAdministrators(1L)).thenReturn(0);
        UserAdministrationService service = service(mapper, mock(OrganizationService.class));

        assertThrows(ResourceConflictException.class,
                () -> service.replaceRoles(1L, List.of(), actor()));
        verify(mapper, never()).replaceUserRoles(anyLong(), any(), any());
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
     * 创建被测服务并注入测试替身。
     *
     * @param mapper 仓储
     * @param organizationService 机构服务
     * @return 用户服务
     */
    private UserAdministrationService service(
            AccessMapper mapper,
            OrganizationService organizationService
    ) {
        return new UserAdministrationServiceImpl(
                mapper, organizationService, mock(PasswordEncoder.class), mock(ManagementAuditService.class)
        );
    }

    /**
     * 创建用户管理测试快照。
     *
     * @param id 用户主键
     * @param organizationCode 机构代码
     * @param enabled 状态
     * @return 用户记录
     */
    private ManagedUserSummary user(long id, String organizationCode, boolean enabled) {
        return new ManagedUserSummary(
                id, id == 1L ? "admin" : "operator", "User", 10L, organizationCode,
                enabled, false, LocalDateTime.now(), LocalDateTime.now(), version()
        );
    }

    /**
     * 创建机构测试快照。
     *
     * @param id 机构主键
     * @param code 机构代码
     * @return 机构记录
     */
    private OrganizationVO organization(long id, String code) {
        return new OrganizationVO(
                id, code, "Organization", "HOSPITAL", null, true,
                null, null, LocalDateTime.now(), LocalDateTime.now(), version()
        );
    }

    /**
     * 创建确定性的8字节SQL Server行版本。
     *
     * @return 固定并发版本
     */
    private byte[] version() {
        return new byte[Long.BYTES];
    }
}
