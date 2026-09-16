package cn.zqkj.platform.system.access.application;

import cn.zqkj.platform.foundation.web.error.InvalidRequestException;
import cn.zqkj.platform.foundation.web.error.ResourceConflictException;
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
        AccessRepository repository = mock(AccessRepository.class);
        when(repository.findRole(1L)).thenReturn(Optional.of(role(true)));
        RoleAdministrationService service = new RoleAdministrationService(repository);

        assertThrows(ResourceConflictException.class,
                () -> service.update(1L, new UpdateRoleCommand("Changed", true, version()), "admin"));
        verify(repository, never()).updateRole(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** 验证角色不能获得未由后端注册的权限代码。 */
    @Test
    void rejectsUnregisteredPermission() {
        AccessRepository repository = mock(AccessRepository.class);
        when(repository.findRole(2L)).thenReturn(Optional.of(role(false)));
        when(repository.countPermissions(List.of("unknown:write"))).thenReturn(0);
        RoleAdministrationService service = new RoleAdministrationService(repository);

        assertThrows(InvalidRequestException.class,
                () -> service.replacePermissions(2L, List.of("unknown:write"), "admin"));
        verify(repository, never()).replaceRolePermissions(
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
