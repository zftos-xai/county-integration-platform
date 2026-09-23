package cn.zqkj.platform.his.service.impl;

import cn.zqkj.platform.exchange.domain.model.ExchangeResult;
import cn.zqkj.platform.exchange.domain.model.ExchangeRuntimeRecord;
import cn.zqkj.platform.exchange.service.ExchangeRuntimeRecordService;
import cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryEntry;
import cn.zqkj.platform.his.domain.organization.model.OrganizationEntry;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证端点自动校验的真实HIS调用被完整、脱敏地写入交换记录。 */
class PhisEndpointVerificationServiceImplTest {

    /** 成功校验应分别记录100-008和100-003，并以端点而非批次关联。 */
    @Test
    void recordsBothEndpointVerificationCallsWithoutPersistingUpstreamText() {
        PhisService phisService = mock(PhisService.class);
        ExchangeRuntimeRecordService records = mock(ExchangeRuntimeRecordService.class);
        when(phisService.verifyOrganizationConfiguration(eq(10L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(true, "OK", List.of(new OrganizationEntry(
                        "HIS-ORG-001", "测试卫生院", null, null, null, null)), null));
        when(phisService.verifyHospitalDirectoryCapability(eq(10L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(true, "OK", List.<HospitalDirectoryEntry>of(), null));
        PhisEndpointVerificationServiceImpl service = new PhisEndpointVerificationServiceImpl(phisService, records);

        service.verify(9L, 10L, ParameterEnvironment.TEST, "测试卫生院", "ORG001", null);

        ArgumentCaptor<ExchangeRuntimeRecord> captor = ArgumentCaptor.forClass(ExchangeRuntimeRecord.class);
        verify(records, times(2)).record(captor.capture());
        List<ExchangeRuntimeRecord> captured = captor.getAllValues();
        assertTrue(captured.stream().allMatch(record -> "ORG001".equals(record.organizationCode())));
        assertTrue(captured.stream().allMatch(record -> "EXTERNAL_ENDPOINT:9".equals(record.sourceRecordId())));
        assertTrue(captured.stream().allMatch(record -> record.result() == ExchangeResult.SUCCESS));
        assertTrue(captured.stream().allMatch(record -> !record.requestSummary().contains("测试卫生院")));
    }

    /** 已经发出的通信失败必须留下结果未知记录，但不能保存异常原文。 */
    @Test
    void recordsCommunicationFailureAsNoResponseWithoutRawExceptionText() {
        PhisService phisService = mock(PhisService.class);
        ExchangeRuntimeRecordService records = mock(ExchangeRuntimeRecordService.class);
        when(phisService.verifyOrganizationConfiguration(anyLong(), eq(ParameterEnvironment.TEST), any()))
                .thenThrow(new PhisCommunicationException("http://secret-host/soap?token=secret"));
        PhisEndpointVerificationServiceImpl service = new PhisEndpointVerificationServiceImpl(phisService, records);

        assertThrows(PhisCommunicationException.class,
                () -> service.verify(9L, 10L, ParameterEnvironment.TEST, "测试卫生院", "ORG001", null));

        ArgumentCaptor<ExchangeRuntimeRecord> captor = ArgumentCaptor.forClass(ExchangeRuntimeRecord.class);
        verify(records).record(captor.capture());
        ExchangeRuntimeRecord record = captor.getValue();
        assertTrue(record.result() == ExchangeResult.NO_RESPONSE);
        assertTrue(!record.resultMessage().contains("secret"));
        assertTrue(!record.communicationErrorSummary().contains("secret"));
    }
}
