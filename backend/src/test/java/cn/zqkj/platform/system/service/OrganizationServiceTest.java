package cn.zqkj.platform.system.service;
import cn.zqkj.platform.system.service.impl.OrganizationServiceImpl;

import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.system.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.domain.dto.UpdateOrganizationCommand;
import cn.zqkj.platform.system.mapper.OrganizationMapper;
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
        OrganizationMapper mapper = mock(OrganizationMapper.class);
        when(mapper.countByCode("ORG001")).thenReturn(1);
        OrganizationService service = new OrganizationServiceImpl(mapper);
        CreateOrganizationCommand command = new CreateOrganizationCommand(
                "ORG001", "County Hospital", "HOSPITAL", null, null, null
        );

        assertThrows(ResourceConflictException.class, () -> service.create(command, "admin"));
        verify(mapper, never()).create(command, "admin");
    }

    /**
     * 验证机构不能通过父链间接引用自身。
     */
    @Test
    void rejectsHierarchyCycle() {
        OrganizationMapper mapper = mock(OrganizationMapper.class);
        when(mapper.findById(1L)).thenReturn(view(1L, null, true));
        when(mapper.findById(2L)).thenReturn(view(2L, 1L, true));
        OrganizationService service = new OrganizationServiceImpl(mapper);
        UpdateOrganizationCommand command = new UpdateOrganizationCommand(
                "County Hospital", "HOSPITAL", 2L, null, null, version()
        );

        assertThrows(ResourceConflictException.class, () -> service.update(1L, command, "admin"));
        verify(mapper, never()).update(1L, command, "admin");
    }

    /**
     * 验证存在已启用子机构时不允许停用父机构。
     */
    @Test
    void rejectsDisableWhenEnabledChildExists() {
        OrganizationMapper mapper = mock(OrganizationMapper.class);
        when(mapper.findById(1L)).thenReturn(view(1L, null, true));
        when(mapper.countEnabledChildren(1L)).thenReturn(1);
        OrganizationService service = new OrganizationServiceImpl(mapper);

        assertThrows(ResourceConflictException.class,
                () -> service.setEnabled(1L, false, version(), "admin"));
        verify(mapper, never()).setEnabled(
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
        OrganizationMapper mapper = mock(OrganizationMapper.class);
        when(mapper.findById(1L)).thenReturn(view(1L, null, true));
        OrganizationService service = new OrganizationServiceImpl(mapper);
        UpdateOrganizationCommand command = new UpdateOrganizationCommand(
                "Updated", "HOSPITAL", null, null, null, version()
        );

        assertThrows(ResourceConflictException.class, () -> service.update(1L, command, "admin"));
        verify(mapper).update(1L, command, "admin");
    }

    /**
     * 验证创建命令在写入前去除可见文本两端空白。
     */
    @Test
    void normalizesCreateText() {
        OrganizationMapper mapper = mock(OrganizationMapper.class);
        when(mapper.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("admin")))
                .thenReturn(1L);
        when(mapper.findById(1L)).thenReturn(view(1L, null, true));
        OrganizationService service = new OrganizationServiceImpl(mapper);
        CreateOrganizationCommand command = new CreateOrganizationCommand(
                " ORG001 ", " County Hospital ", " HOSPITAL ", null,
                LocalDateTime.of(2026, 9, 16, 0, 0), null
        );

        OrganizationVO result = service.create(command, " admin ");

        assertEquals(1L, result.id());
        verify(mapper).create(
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
    private OrganizationVO view(Long id, Long parentId, boolean enabled) {
        return new OrganizationVO(
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
