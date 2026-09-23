package cn.zqkj.platform.his.service.impl;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.exchange.domain.model.ExchangeResult;
import cn.zqkj.platform.exchange.domain.model.ExchangeRuntimeRecord;
import cn.zqkj.platform.exchange.service.ExchangeRuntimeRecordService;
import cn.zqkj.platform.his.domain.endpointverification.model.PhisEndpointVerificationResult;
import cn.zqkj.platform.his.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryType;
import cn.zqkj.platform.his.domain.organization.dto.OrganizationQuery;
import cn.zqkj.platform.his.domain.organization.model.OrganizationEntry;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.domain.protocol.model.PhisTrade;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.his.service.PhisEndpointVerificationService;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * 通过100-008和100-003完成基层HIS端点的自动校验。
 */
@Service
public class PhisEndpointVerificationServiceImpl implements PhisEndpointVerificationService {

    private static final String CALLER_SYSTEM_CODE = "PLATFORM";
    private static final String TARGET_SYSTEM_CODE = "PRIMARY_HIS";
    private static final String ENDPOINT_REFERENCE_PREFIX = "EXTERNAL_ENDPOINT:";

    private final PhisService phisService;
    private final ExchangeRuntimeRecordService exchangeRecords;

    /**
     * 创建基层HIS端点自动校验服务。
     *
     * @param phisService HIS交易调用入口
     * @param exchangeRecords 已发送HIS请求的脱敏运行事实写入边界
     */
    public PhisEndpointVerificationServiceImpl(
            PhisService phisService,
            ExchangeRuntimeRecordService exchangeRecords
    ) {
        this.phisService = phisService;
        this.exchangeRecords = exchangeRecords;
    }

    /**
     * 确认 HIS 来源机构唯一且医院综合目录查询能力可用。
     *
     * <p>通信、协议和配置异常不转换为失败结果，调用方必须按结果未知或配置不可用处理；只有HIS明确
     * 返回拒绝、无法唯一确认机构或机构编码变化才返回拒绝结论。</p>
     */
    @Override
    public PhisEndpointVerificationResult verify(
            long endpointId,
            long organizationId,
            ParameterEnvironment environment,
            String platformOrganizationName,
            String platformOrganizationCode,
            String previousSourceOrganizationId
    ) {
        PhisResponse<List<OrganizationEntry>> response = invokeRecorded(
                endpointId, platformOrganizationCode, PhisTrade.ORGANIZATION_QUERY,
                "端点自动校验；100-008机构查询", () -> phisService.verifyOrganizationConfiguration(
                        organizationId, environment, new OrganizationQuery(platformOrganizationName)));
        if (!response.success()) {
            return PhisEndpointVerificationResult.rejected(
                    "HIS明确拒绝了100-008机构查询");
        }
        List<OrganizationEntry> entries = response.data() == null ? List.of() : response.data().stream()
                .filter(Objects::nonNull)
                .filter(entry -> Func.trimToNull(entry.sourceOrganizationId()) != null)
                .filter(entry -> Func.trimToNull(entry.hospitalName()) != null)
                .toList();
        if (entries.size() != 1) {
            return PhisEndpointVerificationResult.rejected(entries.isEmpty()
                    ? "100-008未找到与填写医院名称对应的机构，请核对名称和授权范围"
                    : "100-008返回多个机构，请填写更准确的医院名称后重新校验");
        }
        OrganizationEntry source = entries.get(0);
        String sourceOrganizationId = Func.trimToNull(source.sourceOrganizationId());
        if (previousSourceOrganizationId != null
                && !previousSourceOrganizationId.equalsIgnoreCase(sourceOrganizationId)) {
            return PhisEndpointVerificationResult.rejected(
                    "100-008返回的机构编码与上次确认结果不一致，请核对HIS机构资料");
        }
        var capability = invokeRecorded(endpointId, platformOrganizationCode,
                PhisTrade.HOSPITAL_DIRECTORY_QUERY, "端点自动校验；100-003目录能力检查",
                () -> phisService.verifyHospitalDirectoryCapability(
                        organizationId, environment, new HospitalDirectoryQuery(
                                HospitalDirectoryType.DEPARTMENT, null,
                                Func.requireText(platformOrganizationCode, "平台机构编码", 64))));
        if (!capability.success()) {
            return PhisEndpointVerificationResult.rejected(
                    "HIS明确拒绝了100-003医院综合目录查询");
        }
        return PhisEndpointVerificationResult.verified(sourceOrganizationId, Func.trimToNull(source.hospitalName()));
    }

    /**
     * 执行端点校验阶段的一次真实HIS请求并保存可审计的最小结果事实。
     *
     * <p>配置或请求参数异常发生在发送前，不能伪造调用记录；通信和协议失败则已发生实际出网尝试，
     * 必须保存受控分类。记录与外部端点而非同步批次关联，且绝不写入SOAP正文、服务地址或凭证。</p>
     *
     * @param endpointId 已保存端点主键
     * @param organizationCode 平台机构编码
     * @param trade 已登记HIS交易
     * @param requestSummary 不含敏感信息的请求用途摘要
     * @param invocation 只执行一次的HIS调用
     * @param <T> HIS响应业务数据类型
     * @return 可确认的HIS响应
     */
    private <T> PhisResponse<T> invokeRecorded(
            long endpointId,
            String organizationCode,
            PhisTrade trade,
            String requestSummary,
            Supplier<PhisResponse<T>> invocation
    ) {
        String requestId = Func.simpleUuid();
        LocalDateTime receivedAt = LocalDateTime.now(ZoneOffset.UTC);
        long startedAt = System.nanoTime();
        PhisResponse<T> response;
        try {
            response = invocation.get();
        } catch (PhisCommunicationException exception) {
            recordInvocation(endpointId, organizationCode, requestId, trade, ExchangeResult.NO_RESPONSE, null,
                    "HIS端点校验结果未知", requestSummary, "通信失败，未取得可确认的HIS响应", startedAt, receivedAt);
            throw exception;
        } catch (PhisProtocolException exception) {
            recordInvocation(endpointId, organizationCode, requestId, trade, ExchangeResult.INVALID_RESPONSE, null,
                    "已收到HIS响应，但无法确认协议结果", requestSummary, null, startedAt, receivedAt);
            throw exception;
        }
        if (response == null) {
            recordInvocation(endpointId, organizationCode, requestId, trade, ExchangeResult.INVALID_RESPONSE, null,
                    "HIS调用未提供可确认的响应对象", requestSummary, null, startedAt, receivedAt);
            throw new PhisProtocolException(trade.code() + "端点校验未返回有效响应；交易号" + requestId);
        }
        boolean successful = response.success();
        long returned = successful && response.data() instanceof List<?> rows ? rows.size() : 0;
        recordInvocation(endpointId, organizationCode, requestId, trade,
                successful ? ExchangeResult.SUCCESS : ExchangeResult.FAILURE, response.resultCode(),
                successful ? trade.code() + "成功；返回" + returned + "条" : "HIS明确拒绝端点校验请求",
                requestSummary, null, startedAt, receivedAt);
        return response;
    }

    /** 保存与端点关联的最小调用事实，不复制HIS业务报文。 */
    private void recordInvocation(
            long endpointId,
            String organizationCode,
            String requestId,
            PhisTrade trade,
            ExchangeResult result,
            String resultCode,
            String resultMessage,
            String requestSummary,
            String communicationErrorSummary,
            long startedAt,
            LocalDateTime receivedAt
    ) {
        exchangeRecords.record(new ExchangeRuntimeRecord(requestId, trade.code(), CALLER_SYSTEM_CODE,
                TARGET_SYSTEM_CODE, Func.requireText(organizationCode, "平台机构编码", 64),
                ENDPOINT_REFERENCE_PREFIX + endpointId, result, resultCode, resultMessage,
                (System.nanoTime() - startedAt) / 1_000_000, requestSummary, communicationErrorSummary,
                receivedAt, LocalDateTime.now(ZoneOffset.UTC)));
    }


}
