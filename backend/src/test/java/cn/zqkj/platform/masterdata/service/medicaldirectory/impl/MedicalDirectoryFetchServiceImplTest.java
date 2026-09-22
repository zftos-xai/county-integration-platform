package cn.zqkj.platform.masterdata.service.medicaldirectory.impl;

import cn.zqkj.platform.his.domain.medicaldirectory.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.his.domain.medicaldirectory.model.MedicalDirectoryEntry;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisBusinessException;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.exchange.service.ExchangeRuntimeRecordService;
import cn.zqkj.platform.exchange.domain.model.ExchangeRuntimeRecord;
import cn.zqkj.platform.exchange.domain.model.ExchangeResult;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import org.mockito.ArgumentCaptor;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.argThat;

/** 验证目录完整取得不会漏页，也不会在来源明确拒绝后继续取得。 */
class MedicalDirectoryFetchServiceImplTest {

    private static final LocalDateTime RANGE_START = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime RANGE_END = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    /** 验证101条来源数据按100条一页的范围完整取回，并交给一次自动校验。 */
    @Test
    void fetchesEveryPageBeforeSingleValidation() {
        PhisService phisService = mock(PhisService.class);
        when(phisService.countMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(true, "1", 101L, null));
        when(phisService.queryMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), any()))
                .thenAnswer(invocation -> {
                    MedicalDirectoryQuery query = invocation.getArgument(2);
                    if (query.startRow() == 1) {
                        return new PhisResponse<>(true, "1", entries(1, 100), null);
                    }
                    return new PhisResponse<>(true, "1", entries(101, 101), null);
                });
        ExchangeRuntimeRecordService records = mock(ExchangeRuntimeRecordService.class);
        MedicalDirectoryFetchServiceImpl service = new MedicalDirectoryFetchServiceImpl(
                phisService, new MedicalDirectoryValidationServiceImpl(), records, "Asia/Shanghai");

        var result = service.fetchAll(batch(), MedicalDirectoryType.CONSUMABLE);

        assertTrue(result.valid());
        assertEquals(101, result.declaredCount());
        assertEquals(101, result.returnedCount());
        assertEquals(101, result.acceptedRecords().size());
        verify(phisService, times(2)).queryMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), any());
        verify(phisService).queryMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), argThat(query ->
                query.startRow() == 1 && query.endRow() == 100));
        verify(phisService).queryMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), argThat(query ->
                query.startRow() == 101 && query.endRow() == 101));
        verify(phisService).countMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), argThat(query ->
                query.sourceOrganizationCode().equals("ORG-008") && query.rangeStart().equals(RANGE_START.plusHours(8))));
        ArgumentCaptor<ExchangeRuntimeRecord> recordedCalls = ArgumentCaptor.forClass(ExchangeRuntimeRecord.class);
        verify(records, times(3)).record(recordedCalls.capture());
        assertEquals(List.of("100-005", "100-004", "100-004"),
                recordedCalls.getAllValues().stream().map(ExchangeRuntimeRecord::interfaceCode).toList());
        assertEquals(List.of("HIS声明101条", "HIS查询成功，返回100条", "HIS查询成功，返回1条"),
                recordedCalls.getAllValues().stream().map(ExchangeRuntimeRecord::resultMessage).toList());
        assertEquals(List.of("耗材；数量查询", "耗材；行范围1—100", "耗材；行范围101—101"),
                recordedCalls.getAllValues().stream().map(ExchangeRuntimeRecord::requestSummary).toList());
        assertTrue(recordedCalls.getAllValues().stream().allMatch(record -> "BD-TEST".equals(record.sourceRecordId())));
    }

    /** 验证来源明确拒绝第二页时立即停止，不能把不完整结果交给后续暂存或发布。 */
    @Test
    void stopsWhenAnyPageIsRejectedByHis() {
        PhisService phisService = mock(PhisService.class);
        when(phisService.countMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(true, "1", 101L, null));
        when(phisService.queryMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), any()))
                .thenAnswer(invocation -> {
                    MedicalDirectoryQuery query = invocation.getArgument(2);
                    if (query.startRow() == 1) {
                        return new PhisResponse<>(true, "1", entries(1, 100), null);
                    }
                    return new PhisResponse<>(false, "0", null, "HIS拒绝目录查询");
                });
        var records = mock(ExchangeRuntimeRecordService.class);
        MedicalDirectoryFetchServiceImpl service = new MedicalDirectoryFetchServiceImpl(
                phisService, new MedicalDirectoryValidationServiceImpl(), records, "Asia/Shanghai");

        PhisBusinessException error = assertThrows(PhisBusinessException.class,
                () -> service.fetchAll(batch(), MedicalDirectoryType.CONSUMABLE));

        assertTrue(error.getMessage().contains("100-004失败（结果码0）"));
        var saved = ArgumentCaptor.forClass(ExchangeRuntimeRecord.class);
        verify(records, times(3)).record(saved.capture());
        assertEquals(ExchangeResult.FAILURE, saved.getAllValues().get(2).result());
        assertTrue(error.getMessage().contains(saved.getAllValues().get(2).requestId()));
        verify(phisService, times(2)).queryMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), any());
    }

    /** 数量查询拒绝时保留错误码、授权分类及交易号，不泄漏任意上游正文，也不继续分页。 */
    @Test
    void recordsCountRejectionWithoutLeakingUpstreamText() {
        var his = mock(PhisService.class);
        var records = mock(ExchangeRuntimeRecordService.class);
        when(his.countMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(PhisResponse.failure("0", "无权访问；secret-value http://private.invalid"));
        var service = new MedicalDirectoryFetchServiceImpl(his, new MedicalDirectoryValidationServiceImpl(), records, "Asia/Shanghai");
        var error = assertThrows(PhisBusinessException.class, () -> service.fetchAll(batch(), MedicalDirectoryType.CONSUMABLE));
        assertTrue(error.getMessage().contains("机构授权未通过"));
        assertTrue(!error.getMessage().contains("secret-value") && !error.getMessage().contains("private.invalid"));
        verify(his, never()).queryMedicalDirectory(org.mockito.ArgumentMatchers.anyLong(), any(), any());
        verify(records).record(argThat(record -> record.result() == ExchangeResult.FAILURE
                && record.targetResultCode().equals("0") && record.sourceRecordId().equals("BD-TEST")
                && !record.resultMessage().contains("secret-value")));
    }

    /** 通信超时留存未知事实并停止，不自动重试或伪造结果码。 */
    @Test
    void recordsUnknownWithoutRetrying() {
        var his = mock(PhisService.class);
        var records = mock(ExchangeRuntimeRecordService.class);
        when(his.countMedicalDirectory(eq(8L), any(), any())).thenThrow(new PhisCommunicationException("超时"));
        var service = new MedicalDirectoryFetchServiceImpl(his, new MedicalDirectoryValidationServiceImpl(), records, "Asia/Shanghai");
        var error = assertThrows(PhisProtocolException.class, () -> service.fetchAll(batch(), MedicalDirectoryType.CONSUMABLE));
        assertTrue(error.getMessage().contains("交易号"));
        verify(his).countMedicalDirectory(eq(8L), any(), any());
        verify(records).record(argThat(record -> record.result() == ExchangeResult.NO_RESPONSE
                && record.targetResultCode() == null && record.communicationErrorSummary() != null));
    }

    /** 已收到但无法解析的响应不能冒充无响应，且不自动重试。 */
    @Test
    void recordsInvalidResponseSeparatelyFromNoResponse() {
        var his = mock(PhisService.class);
        var records = mock(ExchangeRuntimeRecordService.class);
        when(his.countMedicalDirectory(eq(8L), any(), any()))
                .thenThrow(new PhisProtocolException("响应不是合法JSON"));
        var service = new MedicalDirectoryFetchServiceImpl(his, new MedicalDirectoryValidationServiceImpl(),
                records, "Asia/Shanghai");

        var error = assertThrows(PhisProtocolException.class,
                () -> service.fetchAll(batch(), MedicalDirectoryType.CONSUMABLE));

        assertTrue(error.getMessage().contains("响应不符合协议"));
        verify(his).countMedicalDirectory(eq(8L), any(), any());
        verify(records).record(argThat(record -> record.result() == ExchangeResult.INVALID_RESPONSE
                && record.targetResultCode() == null && record.communicationErrorSummary() == null));
    }

    /** 调用适配层若未给出响应对象，记录协议异常而非把它当成HTTP成功。 */
    @Test
    void recordsMissingResponseObjectAsInvalid() {
        var his = mock(PhisService.class);
        var records = mock(ExchangeRuntimeRecordService.class);
        var service = new MedicalDirectoryFetchServiceImpl(his, new MedicalDirectoryValidationServiceImpl(),
                records, "Asia/Shanghai");

        assertThrows(PhisProtocolException.class,
                () -> service.fetchAll(batch(), MedicalDirectoryType.CONSUMABLE));
        verify(records).record(argThat(record -> record.result() == ExchangeResult.INVALID_RESPONSE
                && record.resultMessage().contains("未提供可确认的响应")));
    }

    /** 短页不能继续被误认为完整分页结果。 */
    @Test
    void rejectsShortPageBeforeContinuing() {
        var his = mock(PhisService.class);
        when(his.countMedicalDirectory(eq(8L), any(), any())).thenReturn(PhisResponse.success("1", 101L));
        when(his.queryMedicalDirectory(eq(8L), any(), any())).thenReturn(PhisResponse.success("1", entries(1, 99)));
        var service = new MedicalDirectoryFetchServiceImpl(his, new MedicalDirectoryValidationServiceImpl(),
                mock(ExchangeRuntimeRecordService.class), "Asia/Shanghai");
        assertThrows(PhisBusinessException.class, () -> service.fetchAll(batch(), MedicalDirectoryType.CONSUMABLE));
        verify(his).queryMedicalDirectory(eq(8L), any(), any());
    }

    private MasterDataBatchSnapshot batch() {
        var batch = mock(MasterDataBatchSnapshot.class);
        when(batch.organizationId()).thenReturn(8L);
        when(batch.organizationCode()).thenReturn("ORG-008");
        when(batch.sourceOrganizationId()).thenReturn("ORG-008");
        when(batch.batchNo()).thenReturn("BD-TEST");
        when(batch.environment()).thenReturn(ParameterEnvironment.TEST);
        when(batch.rangeStart()).thenReturn(RANGE_START);
        when(batch.rangeEnd()).thenReturn(RANGE_END);
        return batch;
    }

    /**
     * 创建指定编码区间且字段完整的医疗目录来源记录。
     *
     * @param from 目录编码起始数字
     * @param to 目录编码结束数字
     * @return 所有必填字段均完整的合成来源记录
     */
    private List<MedicalDirectoryEntry> entries(int from, int to) {
        return java.util.stream.IntStream.rangeClosed(from, to)
                .mapToObj(number -> new MedicalDirectoryEntry(
                        "C" + number, "耗材" + number, null, "耗材", "个", null, null, null,
                        null, "2026-09-20 00:00:00", null, null, null, null, null, null,
                        null, null, "1"))
                .toList();
    }
}
