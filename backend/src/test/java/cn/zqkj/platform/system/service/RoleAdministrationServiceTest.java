package cn.zqkj.platform.system.service;
import cn.zqkj.platform.system.service.impl.RoleAdministrationServiceImpl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.system.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.model.RoleSummary;
import cn.zqkj.platform.system.domain.dto.UpdateRoleCommand;
import cn.zqkj.platform.system.mapper.AccessMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证系统角色保护和代码注册权限边界。
 */
class RoleAdministrationServiceTest {

    /** 验证平台管理员系统角色不能通过普通角色API修改。 */
    @Test
    void protectsSystemManagedRole() {
        AccessMapper mapper = mock(AccessMapper.class);
        when(mapper.findRole(1L)).thenReturn(java.util.Optional.of(role(true)));
        RoleAdministrationService service = new RoleAdministrationServiceImpl(
                mapper, mock(ManagementAuditService.class)
        );

        assertThrows(ResourceConflictException.class,
                () -> service.update(1L, new UpdateRoleCommand("Changed", true, version()), actor()));
        verify(mapper, never()).updateRole(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** 验证角色不能获得未由后端注册的权限代码。 */
    @Test
    void rejectsUnregisteredPermission() {
        AccessMapper mapper = mock(AccessMapper.class);
        when(mapper.findRole(2L)).thenReturn(java.util.Optional.of(role(false)));
        when(mapper.countPermissions(List.of("unknown:write"))).thenReturn(0);
        RoleAdministrationService service = new RoleAdministrationServiceImpl(
                mapper, mock(ManagementAuditService.class)
        );

        assertThrows(InvalidRequestException.class,
                () -> service.replacePermissions(2L, List.of("unknown:write"), actor()));
        verify(mapper, never()).replaceRolePermissions(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    /** 验证角色权限替换只记录权限数量和角色标识。 */
    @Test
    void auditsPermissionReplacementWithoutPermissionDetails() {
        AccessMapper mapper = mock(AccessMapper.class);
        ManagementAuditService auditService = mock(ManagementAuditService.class);
        when(mapper.findRole(2L)).thenReturn(Optional.of(role(false)));
        when(mapper.countPermissions(List.of("access:read"))).thenReturn(1);
        when(mapper.findRolePermissionCodes(2L)).thenReturn(List.of("access:read"));
        RoleAdministrationService service = new RoleAdministrationServiceImpl(mapper, auditService);

        service.replacePermissions(2L, List.of("access:read"), actor());

        ArgumentCaptor<ManagementAuditCommand> captor = ArgumentCaptor.forClass(ManagementAuditCommand.class);
        verify(auditService).recordSuccess(captor.capture());
        assertEquals("ROLE_PERMISSIONS_REPLACED", captor.getValue().actionCode());
        assertEquals("OPERATOR", captor.getValue().targetId());
        assertEquals("替换角色权限；权限数量=1", captor.getValue().changeSummary());
    }

    /** @return 固定操作人 */
    private AccessActor actor() {
        return new AccessActor(1L, "admin", Set.of("ORG001"));
    }

    /** @param systemManaged 系统保护标识 @return 角色快照 */
    private RoleSummary role(boolean systemManaged) {
        return new RoleSummary(
                systemManaged ? 1L : 2L,
                systemManaged ? "PLATFORM_ADMIN" : "OPERATOR",
                "Role", true, systemManaged, LocalDateTime.now(), LocalDateTime.now(), version()
        );
    }

    /** @return 固定并发版本 */
    private byte[] version() {
        return new byte[Long.BYTES];
    }
}
