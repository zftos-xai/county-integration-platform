package cn.zqkj.platform.exchange.service;
import cn.zqkj.platform.exchange.service.impl.ExchangeRecordQueryServiceImpl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.exchange.domain.dto.ExchangeRecordQuery;
import cn.zqkj.platform.exchange.mapper.ExchangeRecordMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 验证交换记录查询用例的边界规则。
 */
class ExchangeRecordQueryServiceTest {

    /**
     * 验证过大的查询数量在进入持久化边界前被限制为 100。
     */
    @Test
    void limitsPageSizeToOneHundred() {
        ExchangeRecordMapper mapper = mock(ExchangeRecordMapper.class);
        ExchangeRecordQueryService service = new ExchangeRecordQueryServiceImpl(mapper);

        ExchangeRecordQuery query = new ExchangeRecordQuery(
                "ORG001", null, null, null, null, null, null, 500
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
                LocalDateTime.of(2026, 9, 16, 12, 0),
                LocalDateTime.of(2026, 9, 16, 11, 0),
                null,
                null,
                null,
                null,
                20
        );

        assertThrows(InvalidRequestException.class, () -> service.findRecent(query));
    }

    /**
     * 验证应用服务拒绝无效的非正返回数量，防止非 HTTP 调用绕过控制器校验。
     */
    @Test
    void rejectsNonPositiveLimit() {
        ExchangeRecordMapper mapper = mock(ExchangeRecordMapper.class);
        ExchangeRecordQueryService service = new ExchangeRecordQueryServiceImpl(mapper);
        ExchangeRecordQuery query = new ExchangeRecordQuery(
                "ORG001", null, null, null, null, null, null, 0
        );

        assertThrows(InvalidRequestException.class, () -> service.findRecent(query));
    }
}
