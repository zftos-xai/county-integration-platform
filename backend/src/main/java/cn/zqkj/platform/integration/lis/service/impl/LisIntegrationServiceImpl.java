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
import cn.zqkj.platform.integration.lis.service.LisIntegrationService;
import cn.zqkj.platform.system.configuration.domain.model.ExternalSystemCaller;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.organization.mapper.OrganizationMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 将600-001机器入口编排到已验证基层HIS端点并持久化脱敏交换结果。 */
@Service
public class LisIntegrationServiceImpl implements LisIntegrationService {

    private static final String TARGET_SYSTEM_CODE = "PRIMARY_HIS";
    private static final String TRADE_CODE = "600-001";
    private static final String LIS_PACKAGE_TYPE = "检验";
    private static final String RESULT_UNKNOWN_MESSAGE = "基层HIS调用结果无法确认，请按交换编号核实";

    private final OrganizationMapper organizationMapper;
    private final PhisService phisService;
    private final ExchangeRuntimeRecordService exchangeRecordService;
    private final ParameterEnvironment environment;
    private final Clock clock;

    /**
     * 创建LIS入口编排服务。
     *
     * @param organizationMapper 平台机构代码路由查询
     * @param phisService 基层HIS协议业务服务
     * @param exchangeRecordService 最小交换事实写入服务
     * @param environment 入站LIS调用选用的服务端部署环境，禁止由请求方选择
     */
    @Autowired
    public LisIntegrationServiceImpl(
            OrganizationMapper organizationMapper,
            PhisService phisService,
            ExchangeRuntimeRecordService exchangeRecordService,
            @Value("${platform.integration.his-environment}") ParameterEnvironment environment
    ) {
        this(organizationMapper, phisService, exchangeRecordService,
                environment, Clock.systemUTC());
    }

    /** 创建可注入时钟的编排服务。 */
    LisIntegrationServiceImpl(
            OrganizationMapper organizationMapper,
            PhisService phisService,
            ExchangeRuntimeRecordService exchangeRecordService,
            ParameterEnvironment environment,
            Clock clock
    ) {
        this.organizationMapper = organizationMapper;
        this.phisService = phisService;
        this.exchangeRecordService = exchangeRecordService;
        this.environment = environment;
        this.clock = clock;
    }

    /**
     * 执行一次600-001只读调用；请求机构仅选择目标端点，不参与调用方授权判断。
     *
     * @param caller 已认证外部系统
     * @param request 入站项目包查询
     * @return 平台操作编号、HIS交换编号和查询结果
     */
    @Override
    public LisItemPackageQueryResultVO queryItemPackages(
            ExternalSystemCaller caller,
            LisItemPackageQueryRequest request,
            String operationId
    ) {
        OrganizationVO organization;
        try {
            organization = organizationMapper.findByCode(request.organizationCode());
        } catch (RuntimeException exception) {
            throw LisIntegrationOperationException.platformRoutingUnavailable(operationId);
        }
        if (organization == null || !organization.enabled()) {
            throw LisIntegrationOperationException.notFound(operationId);
        }
        String requestId = java.util.UUID.randomUUID().toString();
        LocalDateTime receivedAt = LocalDateTime.now(clock);
        long startedAt = System.nanoTime();
        try {
            PhisResponse<List<LisItemEntry>> response = phisService.queryLisItemPackages(
                    organization.id(), environment, LIS_PACKAGE_TYPE);
            long durationMs = elapsedMillis(startedAt);
            LocalDateTime processedAt = LocalDateTime.now(clock);
            ExchangeResult result = response.success() ? ExchangeResult.SUCCESS : ExchangeResult.FAILURE;
            recordExchange(operationId, requestId, new ExchangeRuntimeRecord(
                    requestId, TRADE_CODE, caller.systemCode(), TARGET_SYSTEM_CODE,
                    organization.organizationCode(), operationId, result, response.resultCode(),
                    response.success() ? "基层HIS查询成功" : "基层HIS明确拒绝查询",
                    durationMs, "packageType=" + LIS_PACKAGE_TYPE, null,
                    receivedAt, processedAt));
            return new LisItemPackageQueryResultVO(operationId, requestId,
                    response.success() ? "SUCCESS" : "FAILURE", response.resultCode(),
                    response.success() ? "查询成功" : "基层HIS明确拒绝查询",
                    response.success() ? List.copyOf(response.data()) : List.of());
        } catch (PhisCommunicationException exception) {
            recordUnconfirmed(caller, organization, operationId, requestId,
                    receivedAt, startedAt, ExchangeResult.NO_RESPONSE, RESULT_UNKNOWN_MESSAGE);
            return new LisItemPackageQueryResultVO(operationId, requestId, "NO_RESPONSE", null,
                    RESULT_UNKNOWN_MESSAGE, List.of());
        } catch (PhisProtocolException exception) {
            recordUnconfirmed(caller, organization, operationId, requestId,
                    receivedAt, startedAt, ExchangeResult.INVALID_RESPONSE, "基层HIS响应格式无法确认");
            return new LisItemPackageQueryResultVO(operationId, requestId, "INVALID_RESPONSE", null,
                    "基层HIS响应格式无法确认", List.of());
        } catch (PhisConfigurationException exception) {
            throw LisIntegrationOperationException.hisEndpointUnavailable(operationId);
        }
    }

    /** 保存一次实际出站后无法确认目标业务结论的脱敏交换事实。 */
    private void recordUnconfirmed(
            ExternalSystemCaller caller,
            OrganizationVO organization,
            String operationId,
            String requestId,
            LocalDateTime receivedAt,
            long startedAt,
            ExchangeResult result,
            String message
    ) {
        LocalDateTime processedAt = LocalDateTime.now(clock);
        recordExchange(operationId, requestId, new ExchangeRuntimeRecord(
                requestId, TRADE_CODE, caller.systemCode(), TARGET_SYSTEM_CODE,
                organization.organizationCode(), operationId, result, null, message,
                elapsedMillis(startedAt), "packageType=" + LIS_PACKAGE_TYPE,
                result == ExchangeResult.NO_RESPONSE ? message : null,
                receivedAt, processedAt));
    }

    /** 将交换事实写入独立事务；失败时保留本次可供运维定位的两个编号。 */
    private void recordExchange(String operationId, String requestId, ExchangeRuntimeRecord record) {
        try {
            exchangeRecordService.record(record);
        } catch (RuntimeException exception) {
            throw LisIntegrationOperationException.exchangeRecordUnavailable(operationId, requestId);
        }
    }

    /** 以单调时钟计算耗时，避免系统时钟调整造成负数。 */
    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
