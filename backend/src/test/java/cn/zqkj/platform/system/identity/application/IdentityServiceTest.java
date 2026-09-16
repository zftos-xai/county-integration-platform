package cn.zqkj.platform.system.identity.application;

import cn.zqkj.platform.foundation.web.error.InvalidRequestException;
import cn.zqkj.platform.foundation.web.error.ResourceConflictException;
import cn.zqkj.platform.modules.organization.application.OrganizationService;
import cn.zqkj.platform.modules.organization.application.OrganizationView;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

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
 * 验证一次性安全引导和本地密码修改边界。
 */
class IdentityServiceTest {

    private static final String BOOTSTRAP_SECRET = "0123456789abcdef0123456789abcdef";

    /**
     * 验证平台尚无用户且启动密钥存在时可以完成完整初始关系创建。
     */
    @Test
    void bootstrapsFirstAdministrator() {
        IdentityRepository repository = mock(IdentityRepository.class);
        OrganizationService organizationService = mock(OrganizationService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repository.lockAndCountUsers()).thenReturn(0);
        when(organizationService.create(any(), eq("platform-bootstrap"))).thenReturn(organization());
        when(repository.createRole("PLATFORM_ADMIN", "平台管理员", "platform-bootstrap")).thenReturn(20L);
        when(repository.createUser(any(), eq("encoded"), eq(10L), eq("platform-bootstrap"))).thenReturn(30L);
        when(encoder.encode("Initial!Pass123")).thenReturn("encoded");
        IdentityService service = new IdentityService(repository, organizationService, encoder, BOOTSTRAP_SECRET);

        BootstrapResult result = service.bootstrap(BOOTSTRAP_SECRET, command());

        assertEquals(30L, result.userId());
        assertEquals(10L, result.organizationId());
        verify(repository).grantRole(30L, 20L, "platform-bootstrap");
        verify(repository).grantOrganization(30L, 10L, "platform-bootstrap");
        verify(repository).grantPermission(20L, "organization:write", "platform-bootstrap");
    }

    /**
     * 验证错误启动密钥在读取和写入业务数据前被拒绝。
     */
    @Test
    void rejectsInvalidBootstrapSecret() {
        IdentityRepository repository = mock(IdentityRepository.class);
        OrganizationService organizationService = mock(OrganizationService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        IdentityService service = new IdentityService(repository, organizationService, encoder, BOOTSTRAP_SECRET);

        assertThrows(AccessDeniedException.class, () -> service.bootstrap("wrong", command()));
        verify(repository, never()).lockAndCountUsers();
    }

    /**
     * 验证已有用户时并发安全引导被拒绝且不会新建机构。
     */
    @Test
    void rejectsRepeatedBootstrap() {
        IdentityRepository repository = mock(IdentityRepository.class);
        OrganizationService organizationService = mock(OrganizationService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repository.lockAndCountUsers()).thenReturn(1);
        IdentityService service = new IdentityService(repository, organizationService, encoder, BOOTSTRAP_SECRET);

        assertThrows(ResourceConflictException.class, () -> service.bootstrap(BOOTSTRAP_SECRET, command()));
        verify(organizationService, never()).create(any(), any());
    }

    /**
     * 验证安全引导状态不返回密钥且只有未初始化时可用。
     */
    @Test
    void reportsBootstrapStatus() {
        IdentityRepository repository = mock(IdentityRepository.class);
        when(repository.countUsers()).thenReturn(0, 1);
        IdentityService service = new IdentityService(
                repository, mock(OrganizationService.class), mock(PasswordEncoder.class), BOOTSTRAP_SECRET
        );

        assertEquals(new BootstrapStatus(false, true), service.getBootstrapStatus());
        assertEquals(new BootstrapStatus(true, false), service.getBootstrapStatus());
    }

    /**
     * 验证当前密码错误时不会写入新密码哈希。
     */
    @Test
    void rejectsPasswordChangeWithWrongCurrentPassword() {
        IdentityRepository repository = mock(IdentityRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repository.findById(30L)).thenReturn(Optional.of(account()));
        when(encoder.matches("wrong", "hash")).thenReturn(false);
        IdentityService service = new IdentityService(
                repository, mock(OrganizationService.class), encoder, BOOTSTRAP_SECRET
        );

        assertThrows(InvalidRequestException.class,
                () -> service.changePassword(30L, "wrong", "Another!Pass123"));
        verify(repository, never()).changePassword(anyLong(), any(), any());
    }

    /**
     * 创建合法安全引导命令。
     *
     * @return 测试命令
     */
    private BootstrapCommand command() {
        return new BootstrapCommand(
                "ORG001", "县人民医院", "HOSPITAL", "Admin", "平台管理员", "Initial!Pass123"
        );
    }

    /**
     * 创建机构测试快照。
     *
     * @return 机构快照
     */
    private OrganizationView organization() {
        return new OrganizationView(
                10L, "ORG001", "县人民医院", "HOSPITAL", null, true,
                null, null, LocalDateTime.now(), LocalDateTime.now(), new byte[Long.BYTES]
        );
    }

    /**
     * 创建用户认证测试快照。
     *
     * @return 用户快照
     */
    private UserAccount account() {
        return new UserAccount(30L, "admin", "平台管理员", "hash", 10L, "ORG001", true, true);
    }
}
