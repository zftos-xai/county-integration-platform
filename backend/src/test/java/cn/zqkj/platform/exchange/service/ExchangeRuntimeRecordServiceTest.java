package cn.zqkj.platform.exchange.service;
import cn.zqkj.platform.exchange.service.impl.ExchangeRuntimeRecordServiceImpl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.exchange.domain.model.ExchangeResult;
import cn.zqkj.platform.exchange.domain.model.ExchangeRuntimeRecord;
import cn.zqkj.platform.exchange.mapper.ExchangeRecordMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证交换运行记录写入的结果边界和最小数据规则。
 */
class ExchangeRuntimeRecordServiceTest {

    /**
     * 验证包含通信异常摘要且没有目标返回码的无响应记录能够保存。
     */
    @Test
    void savesNoResponseWithCommunicationSummary() {
        ExchangeRecordMapper mapper = mock(ExchangeRecordMapper.class);
        ExchangeRuntimeRecord record = noResponseRecord(null, "连接超时");
        when(mapper.insertRuntimeRecord(record)).thenReturn(1);
        ExchangeRuntimeRecordService service = new ExchangeRuntimeRecordServiceImpl(mapper);

        String requestId = service.record(record);

        assertEquals("req-001", requestId);
        verify(mapper).insertRuntimeRecord(record);
    }

    /**
     * 验证无响应记录不能携带目标返回码，避免把未知结果描述成明确响应。
     */
    @Test
    void rejectsNoResponseWithTargetResultCode() {
        ExchangeRecordMapper mapper = mock(ExchangeRecordMapper.class);
        ExchangeRuntimeRecord record = noResponseRecord("500", "连接中断");
        ExchangeRuntimeRecordService service = new ExchangeRuntimeRecordServiceImpl(mapper);

        assertThrows(InvalidRequestException.class, () -> service.record(record));
        verify(mapper, never()).insertRuntimeRecord(record);
    }

    /**
     * 验证无响应记录必须提供非敏感通信异常摘要，确保运维可以定位事实。
     */
    @Test
    void rejectsNoResponseWithoutCommunicationSummary() {
        ExchangeRecordMapper mapper = mock(ExchangeRecordMapper.class);
        ExchangeRuntimeRecord record = noResponseRecord(null, " ");
        ExchangeRuntimeRecordService service = new ExchangeRuntimeRecordServiceImpl(mapper);

        assertThrows(InvalidRequestException.class, () -> service.record(record));
        verify(mapper, never()).insertRuntimeRecord(record);
    }

    /**
     * 验证结束时间早于开始时间时拒绝写入。
     */
    @Test
    void rejectsProcessedTimeBeforeReceivedTime() {
        ExchangeRecordMapper mapper = mock(ExchangeRecordMapper.class);
        ExchangeRuntimeRecord source = noResponseRecord(null, "连接超时");
        ExchangeRuntimeRecord record = new ExchangeRuntimeRecord(
                source.requestId(),
                source.interfaceCode(),
                source.callerSystemCode(),
                source.targetSystemCode(),
                source.organizationCode(),
                source.sourceRecordId(),
                source.result(),
                source.targetResultCode(),
                source.resultMessage(),
                source.durationMs(),
                source.requestSummary(),
                source.communicationErrorSummary(),
                source.processedAt(),
                source.receivedAt()
        );
        ExchangeRuntimeRecordService service = new ExchangeRuntimeRecordServiceImpl(mapper);

        assertThrows(InvalidRequestException.class, () -> service.record(record));
        verify(mapper, never()).insertRuntimeRecord(record);
    }

    /**
     * 创建不包含医疗正文的无响应测试记录。
     *
     * @param targetResultCode 目标返回码
     * @param communicationErrorSummary 通信异常摘要
     * @return 可用于服务测试的运行记录
     */
    private ExchangeRuntimeRecord noResponseRecord(
            String targetResultCode,
            String communicationErrorSummary
    ) {
        return new ExchangeRuntimeRecord(
                "req-001",
                "TEST-001",
                "TEST_CALLER",
                "TEST_TARGET",
                "ORG001",
                "application-001",
                ExchangeResult.NO_RESPONSE,
                targetResultCode,
                "未取得目标系统响应",
                10_000,
                "申请单引用 application-001",
                communicationErrorSummary,
                LocalDateTime.of(2026, 9, 16, 2, 0),
                LocalDateTime.of(2026, 9, 16, 2, 0, 10)
        );
    }
}
