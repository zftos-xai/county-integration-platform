package cn.zqkj.platform.system.audit.service;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditQuery;
import cn.zqkj.platform.system.audit.mapper.ManagementAuditMapper;
import cn.zqkj.platform.system.audit.service.impl.ManagementAuditServiceImpl;
import jakarta.validation.Validation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证管理审计的脱敏、只追加和机构范围查询规则。 */
class ManagementAuditServiceTest {
    private static final jakarta.validation.ValidatorFactory VALIDATION_FACTORY =
            jakarta.validation.Validation.buildDefaultValidatorFactory();

    /** 释放测试使用的校验器工厂。 */
    @org.junit.jupiter.api.AfterAll
    static void closeValidationFactory() {
        VALIDATION_FACTORY.close();
    }

    /** 长度校验针对实际入库文本，不能因忽略裁剪返回值而放过带尾空白的超长字段。 */
    @Test
    void rejectsOversizedRawAuditFieldsBeforeInsert() {
        ManagementAuditMapper mapper = mock(ManagementAuditMapper.class);
        ManagementAuditService service = new ManagementAuditServiceImpl(mapper, VALIDATION_FACTORY.getValidator());
        ManagementAuditCommand command = new ManagementAuditCommand(null, "directory-job", null, null,
                "A".repeat(64) + " ", "BATCH", "25", "SUCCESS", "同步完成", null);

        assertThrows(InvalidRequestException.class, () -> service.append(command));

        org.mockito.Mockito.verifyNoInteractions(mapper);
    }


    /** 部分同步失败仍可随批次结果保存审计；不得使用数据库不接受的PARTIAL值。 */
    @Test
    void acceptsFailureResultInBusinessTransaction() {
        ManagementAuditMapper mapper = mock(ManagementAuditMapper.class);
        ManagementAuditService service = new ManagementAuditServiceImpl(mapper, VALIDATION_FACTORY.getValidator());
        ManagementAuditCommand command = new ManagementAuditCommand(null, "directory-job", null, null,
                "DIRECTORY_SYNC", "BATCH", "25", "FAILURE", "部分类型同步失败", null);

        service.append(command);

        verify(mapper).insert(command);
        assertThrows(InvalidRequestException.class, () -> service.append(new ManagementAuditCommand(
                null, "directory-job", null, null, "DIRECTORY_SYNC", "BATCH", "25", "PARTIAL", "部分失败", null)));
    }

    /** 验证登录失败只保存不可还原的用户名提示且不包含凭证。 */
    @Test
    void masksLoginFailureActorHint() {
        ManagementAuditMapper mapper = mock(ManagementAuditMapper.class);
        ManagementAuditService service = new ManagementAuditServiceImpl(mapper, VALIDATION_FACTORY.getValidator());

        service.recordLoginFailure("administrator", "REQ-1");

        ArgumentCaptor<ManagementAuditCommand> captor = ArgumentCaptor.forClass(ManagementAuditCommand.class);
        verify(mapper).insert(captor.capture());
        assertEquals("a***", captor.getValue().actorLogin());
        assertEquals("本地登录校验失败；未记录密码或其他凭证", captor.getValue().changeSummary());
    }

    /** 验证查询将当前用户显式机构范围交给Mapper执行过滤。 */
    @Test
    void queriesOnlyActorOrganizationScope() {
        ManagementAuditMapper mapper = mock(ManagementAuditMapper.class);
        ManagementAuditQuery input = new ManagementAuditQuery(" admin ", null, " PARAMETER ", null,
                " REQ-1 ", "SUCCESS", null, null, null, null, 100);
        when(mapper.findVisible(ArgumentMatchers.eq(Set.of("ORG001")),
                ArgumentMatchers.any())).thenReturn(List.of());
        ManagementAuditService service = new ManagementAuditServiceImpl(mapper, VALIDATION_FACTORY.getValidator());

        service.findVisible(Set.of("ORG001"), input);

        ArgumentCaptor<ManagementAuditQuery> captor = ArgumentCaptor.forClass(ManagementAuditQuery.class);
        verify(mapper).findVisible(ArgumentMatchers.eq(Set.of("ORG001")), captor.capture());
        assertEquals("admin", captor.getValue().actorLogin());
        assertEquals("PARAMETER", captor.getValue().targetType());
        assertEquals("REQ-1", captor.getValue().requestId());
    }

    /** 验证审计查询限制最大返回条数。 */
    @Test
    void rejectsUnboundedAuditQuery() {
        ManagementAuditQuery query = new ManagementAuditQuery(null, null, null, null,
                null, null, null, null, null, null, 201);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Assertions.assertFalse(factory.getValidator().validate(query).isEmpty());
        }
    }

    /** 验证继续查询历史记录时，时间和记录编号必须成对出现。 */
    @Test
    void rejectsIncompleteHistoryCursor() {
        ManagementAuditQuery query = new ManagementAuditQuery(null, null, null, null,
                null, null, null, null, LocalDateTime.parse("2026-09-17T12:00:00"), null, 100);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Assertions.assertFalse(factory.getValidator().validate(query).isEmpty());
        }
    }
}
