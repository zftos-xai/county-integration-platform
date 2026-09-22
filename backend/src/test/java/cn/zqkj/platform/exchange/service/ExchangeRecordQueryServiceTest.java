package cn.zqkj.platform.exchange.service;

import cn.zqkj.platform.exchange.domain.dto.ExchangeRecordQuery;
import cn.zqkj.platform.exchange.mapper.ExchangeRecordMapper;
import cn.zqkj.platform.exchange.service.impl.ExchangeRecordQueryServiceImpl;
import jakarta.validation.Validation;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 验证交换记录查询用例的边界规则。
 */
class ExchangeRecordQueryServiceTest {

    /**
     * 查询直接复用已校验输入，不另建截断参数的查询对象。
     */
    @Test
    void passesValidatedQueryWithoutCopying() {
        ExchangeRecordMapper mapper = mock(ExchangeRecordMapper.class);
        ExchangeRecordQueryService service = new ExchangeRecordQueryServiceImpl(mapper);

        ExchangeRecordQuery query = new ExchangeRecordQuery(
                "ORG001", null, null, null, null, null, null, 100
        );

        service.findRecent(query);

        verify(mapper).findRecentByOrganization(argThat(boundedQuery -> boundedQuery.limit() == 100
                && "ORG001".equals(boundedQuery.organizationCode())));
    }

    /**
     * 验证接收时间下界晚于上界时拒绝查询，避免产生含义相反的运行记录筛选。
     */
    @Test
    void rejectsReversedReceivedTimeRange() {
        ExchangeRecordMapper mapper = mock(ExchangeRecordMapper.class);
        ExchangeRecordQueryService service = new ExchangeRecordQueryServiceImpl(mapper);
        ExchangeRecordQuery query = new ExchangeRecordQuery(
                "ORG001",
                OffsetDateTime.parse("2026-09-16T12:00:00Z"),
                OffsetDateTime.parse("2026-09-16T11:00:00Z"),
                null,
                null,
                null,
                null,
                20
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Assertions.assertFalse(factory.getValidator().validate(query).isEmpty());
        }
    }

    /**
     * 入口约束拒绝非正返回数量，内部调用可复用同一约束。
     */
    @Test
    void rejectsNonPositiveLimit() {
        ExchangeRecordMapper mapper = mock(ExchangeRecordMapper.class);
        ExchangeRecordQueryService service = new ExchangeRecordQueryServiceImpl(mapper);
        ExchangeRecordQuery query = new ExchangeRecordQuery(
                "ORG001", null, null, null, null, null, null, 0
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Assertions.assertFalse(factory.getValidator().validate(query).isEmpty());
        }
    }
}
