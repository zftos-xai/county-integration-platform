package cn.zqkj.platform.integration.lis.service.impl;

import cn.zqkj.platform.exchange.domain.model.ExchangeResult;
import cn.zqkj.platform.exchange.domain.model.ExchangeRuntimeRecord;
import cn.zqkj.platform.exchange.service.ExchangeRuntimeRecordService;
import cn.zqkj.platform.his.domain.lis.model.LisItemEntry;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.his.exception.PhisConfigurationException;
import cn.zqkj.platform.integration.lis.exception.LisIntegrationOperationException;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.integration.lis.domain.dto.LisItemPackageQueryRequest;
import cn.zqkj.platform.integration.lis.domain.vo.LisItemPackageQueryResultVO;
import cn.zqkj.platform.system.configuration.domain.model.ExternalSystemCaller;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.organization.mapper.OrganizationMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证600-001机器入口从机构路由到HIS调用和脱敏交换记录的编排规则。 */
@ExtendWith(MockitoExtension.class)
class LisIntegrationServiceImplTest {

    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private PhisService phisService;
    @Mock
    private ExchangeRuntimeRecordService exchangeRecordService;

    private LisIntegrationServiceImpl service;

    /** 为每项用例装配测试服务及单个已启用机构端点。 */
    @BeforeEach
    void setUp() {
        service = new LisIntegrationServiceImpl(organizationMapper, phisService,
                exchangeRecordService, ParameterEnvironment.TEST, java.time.Clock.systemUTC());
    }

    /** 验证认证调用方、已确认HIS机构号、600-001结果和脱敏运行记录贯通。 */
    @Test
    void routesAndRecordsSuccessfulItemPackageQuery() {
        stubOrganization();
        LisItemEntry item = new LisItemEntry("检验包", "P-1", "PC-1", "检验", "12.00",
                "血常规", "I-1", "12.00", "1", "项", "测试机构", "HIS-ORG-17");
        when(phisService.queryLisItemPackages(eq(17L), eq(ParameterEnvironment.TEST), eq("检验")))
                .thenReturn(PhisResponse.success("1", List.of(item)));

        LisItemPackageQueryResultVO result = service.queryItemPackages(
                new ExternalSystemCaller("COUNTY_HOSPITAL", "县医院"),
                new LisItemPackageQueryRequest("ORG-TEST"), "operation-test-1");

        assertEquals("SUCCESS", result.outcome());
        assertEquals("operation-test-1", result.operationId());
        assertEquals(List.of(item), result.items());
        verify(phisService).queryLisItemPackages(17L, ParameterEnvironment.TEST, "检验");
        ArgumentCaptor<ExchangeRuntimeRecord> recordCaptor = ArgumentCaptor.forClass(ExchangeRuntimeRecord.class);
        verify(exchangeRecordService).record(recordCaptor.capture());
        ExchangeRuntimeRecord record = recordCaptor.getValue();
        assertEquals(result.exchangeRequestId(), record.requestId());
        assertEquals("COUNTY_HOSPITAL", record.callerSystemCode());
        assertEquals("PRIMARY_HIS", record.targetSystemCode());
        assertEquals("ORG-TEST", record.organizationCode());
        assertEquals("600-001", record.interfaceCode());
        assertEquals(ExchangeResult.SUCCESS, record.result());
        assertTrue(record.requestSummary().contains("packageType=检验"));
    }

    /** 验证无响应被记为结果未知，且响应不泄露底层通信异常内容。 */
    @Test
    void recordsNoResponseWithoutLeakingCommunicationDetails() {
        stubOrganization();
        when(phisService.queryLisItemPackages(eq(17L), eq(ParameterEnvironment.TEST), eq("检验")))
                .thenThrow(new PhisCommunicationException("通信失败，地址和报文已省略"));

        LisItemPackageQueryResultVO result = service.queryItemPackages(
                new ExternalSystemCaller("COUNTY_HOSPITAL", "县医院"),
                new LisItemPackageQueryRequest("ORG-TEST"), "operation-test-2");

        assertEquals("NO_RESPONSE", result.outcome());
        assertEquals("基层HIS调用结果无法确认，请按交换编号核实", result.message());
        ArgumentCaptor<ExchangeRuntimeRecord> recordCaptor = ArgumentCaptor.forClass(ExchangeRuntimeRecord.class);
        verify(exchangeRecordService).record(recordCaptor.capture());
        assertEquals(ExchangeResult.NO_RESPONSE, recordCaptor.getValue().result());
        assertEquals(null, recordCaptor.getValue().targetResultCode());
        assertTrue(recordCaptor.getValue().communicationErrorSummary().contains("无法确认"));
    }

    /** 验证未知平台机构不会访问HIS或写入目标交换记录。 */
    @Test
    void rejectsUnknownOrganizationBeforeHisCall() {
        when(organizationMapper.findByCode("UNKNOWN")).thenReturn(null);

        LisIntegrationOperationException exception = assertThrows(LisIntegrationOperationException.class,
                () -> service.queryItemPackages(
                new ExternalSystemCaller("COUNTY_HOSPITAL", "县医院"),
                new LisItemPackageQueryRequest("UNKNOWN"), "operation-test-3"));

        assertEquals("ORGANIZATION_NOT_FOUND", exception.errorCode());
        assertEquals("operation-test-3", exception.operationId());
        verify(phisService, never()).queryLisItemPackages(any(Long.class), any(), any());
        verifyNoInteractions(exchangeRecordService);
    }

    /** 验证HIS来源机构配置缺失时返回带操作编号的409且不伪造交换编号。 */
    @Test
    void returnsTraceableConflictWhenHisEndpointIsUnavailable() {
        stubOrganization();
        when(phisService.queryLisItemPackages(eq(17L), eq(ParameterEnvironment.TEST), eq("检验")))
                .thenThrow(new PhisConfigurationException("基层HIS来源机构未确认"));

        LisIntegrationOperationException exception = assertThrows(LisIntegrationOperationException.class,
                () -> service.queryItemPackages(new ExternalSystemCaller("COUNTY_HOSPITAL", "县医院"),
                        new LisItemPackageQueryRequest("ORG-TEST"), "operation-test-4"));

        assertEquals("HIS_ENDPOINT_UNAVAILABLE", exception.errorCode());
        assertEquals("operation-test-4", exception.operationId());
        assertEquals(null, exception.exchangeRequestId());
        verifyNoInteractions(exchangeRecordService);
    }

    /** 验证协议响应无法解析时，交换事实仍记录且不会把目标原文传给调用方。 */
    @Test
    void recordsInvalidTargetResponseAndReturnsExchangeId() {
        stubOrganization();
        when(phisService.queryLisItemPackages(eq(17L), eq(ParameterEnvironment.TEST), eq("检验")))
                .thenThrow(new PhisProtocolException("SOAP响应不符合约定"));

        LisItemPackageQueryResultVO result = service.queryItemPackages(
                new ExternalSystemCaller("COUNTY_HOSPITAL", "县医院"),
                new LisItemPackageQueryRequest("ORG-TEST"), "operation-test-5");

        assertEquals("INVALID_RESPONSE", result.outcome());
        assertEquals("operation-test-5", result.operationId());
        assertEquals("基层HIS响应格式无法确认", result.message());
        ArgumentCaptor<ExchangeRuntimeRecord> recordCaptor = ArgumentCaptor.forClass(ExchangeRuntimeRecord.class);
        verify(exchangeRecordService).record(recordCaptor.capture());
        assertEquals(ExchangeResult.INVALID_RESPONSE, recordCaptor.getValue().result());
        assertEquals(result.exchangeRequestId(), recordCaptor.getValue().requestId());
    }

    /** 验证交换记录落库失败时以503和两个编号报告平台故障。 */
    @Test
    void returnsTraceableUnavailableWhenExchangeRecordCannotBeSaved() {
        stubOrganization();
        when(phisService.queryLisItemPackages(eq(17L), eq(ParameterEnvironment.TEST), eq("检验")))
                .thenReturn(PhisResponse.success("1", List.of()));
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(exchangeRecordService).record(any());

        LisIntegrationOperationException exception = assertThrows(LisIntegrationOperationException.class,
                () -> service.queryItemPackages(new ExternalSystemCaller("COUNTY_HOSPITAL", "县医院"),
                        new LisItemPackageQueryRequest("ORG-TEST"), "operation-test-6"));

        assertEquals("EXCHANGE_RECORD_UNAVAILABLE", exception.errorCode());
        assertEquals("operation-test-6", exception.operationId());
        assertTrue(exception.exchangeRequestId() != null && !exception.exchangeRequestId().isBlank());
    }

    /** 构造一条测试平台机构记录。 */
    private OrganizationVO organization() {
        return new OrganizationVO(17L, "ORG-TEST", "测试机构", "基层医疗机构", null, true,
                null, null, LocalDateTime.now(), LocalDateTime.now(), new byte[8]);
    }

    /** 为调用编排用例提供合成的平台机构档案。 */
    private void stubOrganization() {
        when(organizationMapper.findByCode("ORG-TEST")).thenReturn(organization());
    }
}
