package cn.zqkj.platform.system.service;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.system.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.domain.dto.ManagementAuditQuery;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.mapper.ManagementAuditMapper;
import cn.zqkj.platform.system.service.impl.ManagementAuditServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证管理审计的脱敏、只追加和机构范围查询规则。 */
class ManagementAuditServiceTest {

    /** 验证登录失败只保存不可还原的用户名提示且不包含凭证。 */
    @Test
    void masksLoginFailureActorHint() {
        ManagementAuditMapper mapper = mock(ManagementAuditMapper.class);
        ManagementAuditService service = new ManagementAuditServiceImpl(mapper);

        service.recordLoginFailure("administrator", "REQ-1");

        ArgumentCaptor<ManagementAuditCommand> captor = ArgumentCaptor.forClass(ManagementAuditCommand.class);
        verify(mapper).insert(captor.capture());
        assertEquals("a***", captor.getValue().actorHint());
        assertEquals("本地登录校验失败；未记录密码或其他凭证", captor.getValue().changeSummary());
    }

    /** 验证查询将当前用户显式机构范围交给Mapper执行过滤。 */
    @Test
    void queriesOnlyActorOrganizationScope() {
        ManagementAuditMapper mapper = mock(ManagementAuditMapper.class);
        ManagementAuditQuery input = new ManagementAuditQuery(" admin ", null, " PARAMETER ", null,
                " REQ-1 ", "SUCCESS", null, null, null, null, 100);
        when(mapper.findVisible(org.mockito.ArgumentMatchers.eq(Set.of("ORG001")),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        ManagementAuditService service = new ManagementAuditServiceImpl(mapper);

        service.findVisible(new AccessActor(1L, "admin", Set.of("ORG001")), input);

        ArgumentCaptor<ManagementAuditQuery> captor = ArgumentCaptor.forClass(ManagementAuditQuery.class);
        verify(mapper).findVisible(org.mockito.ArgumentMatchers.eq(Set.of("ORG001")), captor.capture());
        assertEquals("admin", captor.getValue().actorLogin());
        assertEquals("PARAMETER", captor.getValue().targetType());
        assertEquals("REQ-1", captor.getValue().requestId());
    }

    /** 验证审计查询限制最大返回条数。 */
    @Test
    void rejectsUnboundedAuditQuery() {
        ManagementAuditService service = new ManagementAuditServiceImpl(mock(ManagementAuditMapper.class));
        ManagementAuditQuery query = new ManagementAuditQuery(null, null, null, null,
                null, null, null, null, null, null, 201);
        assertThrows(InvalidRequestException.class, () -> service.findVisible(
                new AccessActor(1L, "admin", Set.of("ORG001")), query));
    }

    /** 验证继续查询历史记录时，时间和记录编号必须成对出现。 */
    @Test
    void rejectsIncompleteHistoryCursor() {
        ManagementAuditService service = new ManagementAuditServiceImpl(mock(ManagementAuditMapper.class));
        ManagementAuditQuery query = new ManagementAuditQuery(null, null, null, null,
                null, null, null, null, java.time.LocalDateTime.parse("2026-09-17T12:00:00"), null, 100);
        assertThrows(InvalidRequestException.class, () -> service.findVisible(
                new AccessActor(1L, "admin", Set.of("ORG001")), query));
    }
}
