package cn.zqkj.platform.modules.organization.application;

import cn.zqkj.platform.foundation.web.error.ResourceConflictException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * 验证机构编码、层级、停用和并发边界。
 */
class OrganizationServiceTest {

    /**
     * 验证重复机构编码在进入写入边界前被拒绝。
     */
    @Test
    void rejectsDuplicateOrganizationCode() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        when(repository.existsByCode("ORG001")).thenReturn(true);
        OrganizationService service = new OrganizationService(repository);
        CreateOrganizationCommand command = new CreateOrganizationCommand(
                "ORG001", "County Hospital", "HOSPITAL", null, null, null
        );

        assertThrows(ResourceConflictException.class, () -> service.create(command, "admin"));
        verify(repository, never()).create(command, "admin");
    }

    /**
     * 验证机构不能通过父链间接引用自身。
     */
    @Test
    void rejectsHierarchyCycle() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(view(1L, null, true)));
        when(repository.findById(2L)).thenReturn(Optional.of(view(2L, 1L, true)));
        OrganizationService service = new OrganizationService(repository);
        UpdateOrganizationCommand command = new UpdateOrganizationCommand(
                "County Hospital", "HOSPITAL", 2L, null, null, version()
        );

        assertThrows(ResourceConflictException.class, () -> service.update(1L, command, "admin"));
        verify(repository, never()).update(1L, command, "admin");
    }

    /**
     * 验证存在已启用子机构时不允许停用父机构。
     */
    @Test
    void rejectsDisableWhenEnabledChildExists() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(view(1L, null, true)));
        when(repository.hasEnabledChildren(1L)).thenReturn(true);
        OrganizationService service = new OrganizationService(repository);

        assertThrows(ResourceConflictException.class,
                () -> service.setEnabled(1L, false, version(), "admin"));
        verify(repository, never()).setEnabled(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(false),
                any(byte[].class),
                org.mockito.ArgumentMatchers.eq("admin")
        );
    }

    /**
     * 验证并发版本不匹配时返回资源冲突。
     */
    @Test
    void reportsConcurrentUpdateConflict() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(view(1L, null, true)));
        OrganizationService service = new OrganizationService(repository);
        UpdateOrganizationCommand command = new UpdateOrganizationCommand(
                "Updated", "HOSPITAL", null, null, null, version()
        );

        assertThrows(ResourceConflictException.class, () -> service.update(1L, command, "admin"));
        verify(repository).update(1L, command, "admin");
    }

    /**
     * 验证创建命令在写入前去除可见文本两端空白。
     */
    @Test
    void normalizesCreateText() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        OrganizationView created = view(1L, null, true);
        when(repository.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("admin")))
                .thenReturn(created);
        OrganizationService service = new OrganizationService(repository);
        CreateOrganizationCommand command = new CreateOrganizationCommand(
                " ORG001 ", " County Hospital ", " HOSPITAL ", null,
                LocalDateTime.of(2026, 9, 16, 0, 0), null
        );

        OrganizationView result = service.create(command, " admin ");

        assertEquals(1L, result.id());
        verify(repository).create(
                org.mockito.ArgumentMatchers.argThat(value -> "ORG001".equals(value.organizationCode())
                        && "County Hospital".equals(value.organizationName())
                        && "HOSPITAL".equals(value.organizationType())),
                org.mockito.ArgumentMatchers.eq("admin")
        );
    }

    /**
     * 创建机构测试快照。
     *
     * @param id 机构主键
     * @param parentId 可选父机构主键
     * @param enabled 启用状态
     * @return 机构快照
     */
    private OrganizationView view(Long id, Long parentId, boolean enabled) {
        return new OrganizationView(
                id, "ORG" + id, "Organization " + id, "HOSPITAL", parentId, enabled,
                null, null, LocalDateTime.now(), LocalDateTime.now(), version()
        );
    }

    /**
     * 创建固定长度的测试并发版本。
     *
     * @return 8字节版本
     */
    private byte[] version() {
        return new byte[Long.BYTES];
    }
}
