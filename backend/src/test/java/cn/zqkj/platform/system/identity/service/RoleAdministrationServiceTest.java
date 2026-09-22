package cn.zqkj.platform.system.identity.service;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import cn.zqkj.platform.system.identity.domain.dto.UpdateRoleCommand;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.identity.domain.model.RolePermissionAssignment;
import cn.zqkj.platform.system.identity.domain.model.RoleSummary;
import cn.zqkj.platform.system.identity.mapper.AccessMapper;
import cn.zqkj.platform.system.identity.service.impl.RoleAdministrationServiceImpl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证系统角色保护和代码注册权限边界。
 */
class RoleAdministrationServiceTest {

    /** 角色列表一次读取全部关联权限，避免按角色逐条查询。 */
    @Test
    void listsRolesWithBulkPermissions() {
        AccessMapper mapper = mock(AccessMapper.class);
        RoleSummary first = role(true);
        RoleSummary second = role(false);
        when(mapper.findRoles()).thenReturn(List.of(first, second));
        when(mapper.findRolePermissions()).thenReturn(List.of(
                new RolePermissionAssignment(1L, "access:read"),
                new RolePermissionAssignment(2L, "access:write")));

        var roles = new RoleAdministrationServiceImpl(mapper, mock(ManagementAuditService.class)).findAll();

        assertEquals(List.of("access:read"), roles.get(0).permissionCodes());
        assertEquals(List.of("access:write"), roles.get(1).permissionCodes());
        verify(mapper, never()).findRolePermissionCodes(anyLong());
    }

    /** 验证平台管理员系统角色不能通过普通角色API修改。 */
    @Test
    void protectsSystemManagedRole() {
        AccessMapper mapper = mock(AccessMapper.class);
        when(mapper.findRole(1L)).thenReturn(Optional.of(role(true)));
        RoleAdministrationService service = new RoleAdministrationServiceImpl(
                mapper, mock(ManagementAuditService.class)
        );

        assertThrows(ResourceConflictException.class,
                () -> service.update(1L, new UpdateRoleCommand("Changed", true, version()), actor()));
        verify(mapper, never()).updateRole(ArgumentMatchers.anyLong(),
                ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    /** 验证角色不能获得未由后端注册的权限代码。 */
    @Test
    void rejectsUnregisteredPermission() {
        AccessMapper mapper = mock(AccessMapper.class);
        when(mapper.findRole(2L)).thenReturn(Optional.of(role(false)));
        when(mapper.countPermissions(List.of("unknown:write"))).thenReturn(0);
        RoleAdministrationService service = new RoleAdministrationServiceImpl(
                mapper, mock(ManagementAuditService.class)
        );

        assertThrows(InvalidRequestException.class,
                () -> service.replacePermissions(2L, List.of("unknown:write"), actor()));
        verify(mapper, never()).replaceRolePermissions(
                ArgumentMatchers.anyLong(), ArgumentMatchers.any(),
                ArgumentMatchers.any()
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
        verify(auditService).append(captor.capture());
        assertEquals("ROLE_PERMISSIONS_REPLACED", captor.getValue().actionCode());
        assertEquals("OPERATOR", captor.getValue().targetId());
        assertEquals("替换角色权限；权限数量=1", captor.getValue().changeSummary());
    }

    /** 验证仍分配给用户的角色不能删除。 */
    @Test
    void rejectsDeletingAssignedRole() {
        AccessMapper mapper = mock(AccessMapper.class);
        when(mapper.findRole(2L)).thenReturn(Optional.of(role(false)));
        when(mapper.countUsersByRole(2L)).thenReturn(2);
        RoleAdministrationService service = new RoleAdministrationServiceImpl(
                mapper, mock(ManagementAuditService.class)
        );

        assertThrows(ResourceConflictException.class, () -> service.delete(2L, version(), actor()));
        verify(mapper, never()).deleteRole(ArgumentMatchers.anyLong(),
                ArgumentMatchers.any());
    }

    /** 验证未分配用户的非系统角色可连同权限关系删除。 */
    @Test
    void deletesUnusedCustomRole() {
        AccessMapper mapper = mock(AccessMapper.class);
        when(mapper.findRole(2L)).thenReturn(Optional.of(role(false)));
        when(mapper.deleteRole(ArgumentMatchers.eq(2L),
                ArgumentMatchers.any())).thenReturn(1);
        RoleAdministrationService service = new RoleAdministrationServiceImpl(
                mapper, mock(ManagementAuditService.class)
        );

        service.delete(2L, version(), actor());

        verify(mapper).deleteRolePermissions(2L);
        verify(mapper).deleteRole(ArgumentMatchers.eq(2L),
                ArgumentMatchers.any());
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
     * 创建角色测试快照。
     *
     * @param systemManaged 系统保护标识
     * @return 角色记录
     */
    private RoleSummary role(boolean systemManaged) {
        return new RoleSummary(
                systemManaged ? 1L : 2L,
                systemManaged ? "PLATFORM_ADMIN" : "OPERATOR",
                "Role", true, systemManaged, LocalDateTime.now(), LocalDateTime.now(), version()
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
