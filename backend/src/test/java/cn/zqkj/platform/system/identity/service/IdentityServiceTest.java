package cn.zqkj.platform.system.identity.service;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.system.bootstrap.domain.dto.BootstrapCommand;
import cn.zqkj.platform.system.bootstrap.domain.vo.BootstrapResultVO;
import cn.zqkj.platform.system.bootstrap.domain.vo.BootstrapStatusVO;
import cn.zqkj.platform.system.identity.domain.model.UserAccount;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import cn.zqkj.platform.system.identity.service.impl.IdentityServiceImpl;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.organization.service.OrganizationService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

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
        IdentityMapper mapper = mock(IdentityMapper.class);
        OrganizationService organizationService = mock(OrganizationService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(mapper.lockAndCountUsers()).thenReturn(0);
        when(organizationService.create(any(), eq("platform-bootstrap"))).thenReturn(organization());
        when(mapper.createRole("PLATFORM_ADMIN", "平台管理员", "platform-bootstrap")).thenReturn(20L);
        when(mapper.createUser(any(), eq("encoded"), eq(10L), eq("platform-bootstrap"))).thenReturn(30L);
        when(encoder.encode("Initial!Pass123")).thenReturn("encoded");
        IdentityService service = new IdentityServiceImpl(mapper, organizationService, encoder, BOOTSTRAP_SECRET);

        BootstrapResultVO result = service.bootstrap(BOOTSTRAP_SECRET, command());

        assertEquals(30L, result.userId());
        assertEquals(10L, result.organizationId());
        verify(mapper).grantRole(30L, 20L, "platform-bootstrap");
        verify(mapper).grantOrganization(30L, 10L, "platform-bootstrap");
        verify(mapper).grantPermission(20L, "organization:write", "platform-bootstrap");
        verify(mapper).upsertPermission("exchange:read", "查询交换记录");
        verify(mapper).grantPermission(20L, "exchange:read", "platform-bootstrap");
    }

    /**
     * 验证错误启动密钥在读取和写入业务数据前被拒绝。
     */
    @Test
    void rejectsInvalidBootstrapSecret() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        OrganizationService organizationService = mock(OrganizationService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        IdentityService service = new IdentityServiceImpl(mapper, organizationService, encoder, BOOTSTRAP_SECRET);

        assertThrows(AccessDeniedException.class, () -> service.bootstrap("wrong", command()));
        verify(mapper, never()).lockAndCountUsers();
    }

    /**
     * 验证已有用户时并发安全引导被拒绝且不会新建机构。
     */
    @Test
    void rejectsRepeatedBootstrap() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        OrganizationService organizationService = mock(OrganizationService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(mapper.lockAndCountUsers()).thenReturn(1);
        IdentityService service = new IdentityServiceImpl(mapper, organizationService, encoder, BOOTSTRAP_SECRET);

        assertThrows(ResourceConflictException.class, () -> service.bootstrap(BOOTSTRAP_SECRET, command()));
        verify(organizationService, never()).create(any(), any());
    }

    /**
     * 验证安全引导状态不返回密钥且只有未初始化时可用。
     */
    @Test
    void reportsBootstrapStatus() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        when(mapper.countUsers()).thenReturn(0, 1);
        IdentityService service = new IdentityServiceImpl(
                mapper, mock(OrganizationService.class), mock(PasswordEncoder.class), BOOTSTRAP_SECRET
        );

        assertEquals(new BootstrapStatusVO(false, true), service.getBootstrapStatus());
        assertEquals(new BootstrapStatusVO(true, false), service.getBootstrapStatus());
    }

    /**
     * 验证当前密码错误时不会写入新密码哈希。
     */
    @Test
    void rejectsPasswordChangeWithWrongCurrentPassword() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(mapper.findById(30L)).thenReturn(account());
        when(encoder.matches("wrong", "hash")).thenReturn(false);
        IdentityService service = new IdentityServiceImpl(
                mapper, mock(OrganizationService.class), encoder, BOOTSTRAP_SECRET
        );

        assertThrows(InvalidRequestException.class,
                () -> service.changePassword(30L, "wrong", "Another!Pass123"));
        verify(mapper, never()).changePassword(anyLong(), any(), any(), any());
    }

    /** 两个改密请求验证同一旧密码后，只有持有当前账号版本的写入可以成功。 */
    @Test
    void rejectsPasswordChangeWhenAccountVersionHasChanged() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserAccount current = account();
        when(mapper.findById(30L)).thenReturn(current);
        when(encoder.matches("old", "hash")).thenReturn(true);
        when(encoder.encode("Another!Pass123")).thenReturn("new-hash");
        IdentityService service = new IdentityServiceImpl(
                mapper, mock(OrganizationService.class), encoder, BOOTSTRAP_SECRET);
        assertThrows(ResourceConflictException.class, () -> service.changePassword(30L, "old", "Another!Pass123"));
        verify(mapper).changePassword(30L, "new-hash", "admin", current.version());
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
     * @return 机构记录
     */
    private OrganizationVO organization() {
        return new OrganizationVO(
                10L, "ORG001", "县人民医院", "HOSPITAL", null, true,
                null, null, LocalDateTime.now(), LocalDateTime.now(), new byte[Long.BYTES]
        );
    }

    /**
     * 创建用户认证测试快照。
     *
     * @return 用户记录
     */
    private UserAccount account() {
        return new UserAccount(30L, "admin", "平台管理员", "hash", 10L, "ORG001", true, true, new byte[8]);
    }
}
