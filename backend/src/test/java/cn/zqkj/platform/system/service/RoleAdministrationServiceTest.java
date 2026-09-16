package cn.zqkj.platform.system.service;
import cn.zqkj.platform.system.service.impl.RoleAdministrationServiceImpl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.system.domain.model.RoleSummary;
import cn.zqkj.platform.system.domain.dto.UpdateRoleCommand;
import cn.zqkj.platform.system.mapper.AccessMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
        RoleAdministrationService service = new RoleAdministrationServiceImpl(mapper);

        assertThrows(ResourceConflictException.class,
                () -> service.update(1L, new UpdateRoleCommand("Changed", true, version()), "admin"));
        verify(mapper, never()).updateRole(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** 验证角色不能获得未由后端注册的权限代码。 */
    @Test
    void rejectsUnregisteredPermission() {
        AccessMapper mapper = mock(AccessMapper.class);
        when(mapper.findRole(2L)).thenReturn(java.util.Optional.of(role(false)));
        when(mapper.countPermissions(List.of("unknown:write"))).thenReturn(0);
        RoleAdministrationService service = new RoleAdministrationServiceImpl(mapper);

        assertThrows(InvalidRequestException.class,
                () -> service.replacePermissions(2L, List.of("unknown:write"), "admin"));
        verify(mapper, never()).replaceRolePermissions(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
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
