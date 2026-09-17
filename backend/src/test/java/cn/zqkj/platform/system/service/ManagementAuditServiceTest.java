package cn.zqkj.platform.system.service;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.system.domain.dto.ManagementAuditCommand;
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

    /** 验证登录失败只保存不可还原的主体提示且不包含凭证。 */
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

    /** 验证查询将当前主体显式机构范围交给Mapper执行过滤。 */
    @Test
    void queriesOnlyActorOrganizationScope() {
        ManagementAuditMapper mapper = mock(ManagementAuditMapper.class);
        when(mapper.findVisible(Set.of("ORG001"), "PARAMETER", null, 100)).thenReturn(List.of());
        ManagementAuditService service = new ManagementAuditServiceImpl(mapper);

        service.findVisible(new AccessActor(1L, "admin", Set.of("ORG001")), "PARAMETER", null, 100);

        verify(mapper).findVisible(Set.of("ORG001"), "PARAMETER", null, 100);
    }

    /** 验证审计查询限制最大返回条数。 */
    @Test
    void rejectsUnboundedAuditQuery() {
        ManagementAuditService service = new ManagementAuditServiceImpl(mock(ManagementAuditMapper.class));
        assertThrows(InvalidRequestException.class, () -> service.findVisible(
                new AccessActor(1L, "admin", Set.of("ORG001")), null, null, 201
        ));
    }
}
