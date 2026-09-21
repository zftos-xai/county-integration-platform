package cn.zqkj.platform.his.service.impl;

import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.his.client.PhisInvocationContext;
import cn.zqkj.platform.his.client.PhisProtocolClient;
import cn.zqkj.platform.his.client.PhisRequestValidator;
import cn.zqkj.platform.his.domain.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.his.domain.dto.MedicalDirectoryCountQuery;
import cn.zqkj.platform.his.domain.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.his.domain.dto.OrganizationQuery;
import cn.zqkj.platform.his.domain.model.HospitalDirectoryEntry;
import cn.zqkj.platform.his.domain.model.MedicalDirectoryEntry;
import cn.zqkj.platform.his.domain.model.OrganizationEntry;
import cn.zqkj.platform.his.domain.model.PhisResponse;
import cn.zqkj.platform.his.domain.model.PhisTrade;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisConfigurationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.his.exception.PhisRequestException;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.system.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.domain.model.ExternalEndpointAuthentication;
import cn.zqkj.platform.system.domain.model.ExternalEndpointRuntimeConfiguration;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.service.ExternalEndpointResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

/**
 * 解析机构配置、装配可信认证字段并通过SOAP适配器调用基层HIS。
 */
@Service
public class PhisServiceImpl implements PhisService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhisServiceImpl.class);

    /** 基层HIS在外部系统配置中的稳定代码。 */
    public static final String SYSTEM_CODE = "PRIMARY_HIS";

    private final ExternalEndpointResolutionService endpointResolutionService;
    private final PhisProtocolClient phisProtocolClient;

    /**
     * 创建HIS应用服务并注入端点解析和协议调用依赖。
     *
     * @param endpointResolutionService 外部系统运行配置服务
     * @param phisProtocolClient SOAP协议适配器
     */
    public PhisServiceImpl(
            ExternalEndpointResolutionService endpointResolutionService,
            PhisProtocolClient phisProtocolClient
    ) {
        this.endpointResolutionService = endpointResolutionService;
        this.phisProtocolClient = phisProtocolClient;
    }

    /**
     * {@inheritDoc}
     *
     * <p>仅使用已启用且校验通过的机构端点；按“未发送、结果未知、完成”区分运行日志，
     * 不记录地址、请求正文或认证信息。</p>
     */
    @Override
    public PhisResponse<List<HospitalDirectoryEntry>> queryHospitalDirectory(
            long organizationId,
            ParameterEnvironment environment,
            HospitalDirectoryQuery query
    ) {
        return execute(organizationId, environment, PhisTrade.HOSPITAL_DIRECTORY_QUERY,
                EndpointRequirement.ENABLED, () -> PhisRequestValidator.validate(query), context ->
                        phisProtocolClient.queryHospitalDirectory(context, query));
    }

    /**
     * {@inheritDoc}
     *
     * <p>供端点启用前的能力校验使用，允许读取已配置但尚未验证的端点，且不绕过机构绑定检查。</p>
     */
    @Override
    public PhisResponse<List<HospitalDirectoryEntry>> verifyHospitalDirectoryCapability(
            long organizationId,
            ParameterEnvironment environment,
            HospitalDirectoryQuery query
    ) {
        return execute(organizationId, environment, PhisTrade.HOSPITAL_DIRECTORY_QUERY,
                EndpointRequirement.CONFIGURED, () -> PhisRequestValidator.validate(query), context ->
                        phisProtocolClient.queryHospitalDirectory(context, query));
    }

    /**
     * {@inheritDoc}
     *
     * <p>解析已启用端点后调用100-004，并将通信或协议异常记录为结果未知；不在此层自动重试。</p>
     */
    @Override
    public PhisResponse<List<MedicalDirectoryEntry>> queryMedicalDirectory(
            long organizationId, ParameterEnvironment environment, MedicalDirectoryQuery query
    ) {
        return execute(organizationId, environment, PhisTrade.MEDICAL_DIRECTORY_QUERY,
                EndpointRequirement.ENABLED, () -> PhisRequestValidator.validate(query), context ->
                        phisProtocolClient.queryMedicalDirectory(context, query));
    }

    /**
     * {@inheritDoc}
     *
     * <p>解析已启用端点后调用100-005，并保留通信失败与HIS业务失败的语义差异。</p>
     */
    @Override
    public PhisResponse<Long> countMedicalDirectory(
            long organizationId, ParameterEnvironment environment, MedicalDirectoryCountQuery query
    ) {
        return execute(organizationId, environment, PhisTrade.MEDICAL_DIRECTORY_COUNT,
                EndpointRequirement.ENABLED, () -> PhisRequestValidator.validate(query), context ->
                        phisProtocolClient.countMedicalDirectory(context, query));
    }

    /**
     * {@inheritDoc}
     *
     * <p>使用已启用端点执行100-008，并统一输出不含敏感配置的运行日志。</p>
     */
    @Override
    public PhisResponse<List<OrganizationEntry>> queryOrganizations(
            long organizationId,
            ParameterEnvironment environment,
            OrganizationQuery query
    ) {
        return execute(organizationId, environment, PhisTrade.ORGANIZATION_QUERY,
                EndpointRequirement.ENABLED, () -> PhisRequestValidator.validate(query), context ->
                        phisProtocolClient.queryOrganizations(context, query));
    }

    /**
     * {@inheritDoc}
     *
     * <p>供端点配置校验使用，可调用尚未启用的已配置端点，但仍执行参数和机构范围校验。</p>
     */
    @Override
    public PhisResponse<List<OrganizationEntry>> verifyOrganizationConfiguration(
            long organizationId,
            ParameterEnvironment environment,
            OrganizationQuery query
    ) {
        return execute(organizationId, environment, PhisTrade.ORGANIZATION_QUERY,
                EndpointRequirement.CONFIGURED, () -> PhisRequestValidator.validate(query), context ->
                        phisProtocolClient.queryOrganizations(context, query));
    }

    /**
     * 使用统一模板解析调用上下文、执行协议调用并记录不含敏感信息的结果。
     *
     * @param organizationId 平台机构主键
     * @param environment 部署环境
     * @param trade 已登记交易
     * @param endpointRequirement 允许使用的端点状态
     * @param requestValidation 不访问配置和网络的发送前查询校验
     * @param operation 只接收完整调用上下文的强类型协议操作
     * @param <T> 业务响应数据类型
     * @return 强类型业务响应
     */
    private <T> PhisResponse<T> execute(
            long organizationId,
            ParameterEnvironment environment,
            PhisTrade trade,
            EndpointRequirement endpointRequirement,
            Runnable requestValidation,
            Function<PhisInvocationContext, PhisResponse<T>> operation
    ) {
        long startedAt = System.nanoTime();
        try {
            requestValidation.run();
            PhisInvocationContext context = resolveInvocationContext(
                    organizationId, environment, endpointRequirement);
            PhisResponse<T> response = operation.apply(context);
            if (response == null) {
                throw new PhisProtocolException("基层HIS协议客户端未返回响应");
            }
            logCompletion(organizationId, environment, trade, response, startedAt);
            return response;
        } catch (PhisConfigurationException | PhisRequestException exception) {
            logFailure(organizationId, environment, trade, "未发送", exception.getMessage(), startedAt);
            throw exception;
        } catch (PhisCommunicationException | PhisProtocolException exception) {
            logFailure(organizationId, environment, trade, "结果未知",
                    conciseReason(exception.getMessage()), startedAt);
            throw exception;
        } catch (RuntimeException exception) {
            logFailure(organizationId, environment, trade,
                    "状态不确定", "未分类运行错误", startedAt);
            throw exception;
        }
    }

    /**
     * 按端点状态要求解析当前机构的HIS运行参数。
     *
     * @param organizationId 平台机构主键
     * @param environment 运行环境
     * @param endpointRequirement 允许使用的端点状态
     * @return 已校验的调用参数
     */
    private PhisInvocationContext resolveInvocationContext(
            long organizationId,
            ParameterEnvironment environment,
            EndpointRequirement endpointRequirement
    ) {
        if (organizationId <= 0 || environment == null) {
            throw new PhisConfigurationException("必须提供有效机构和部署环境");
        }
        ExternalEndpointRuntimeConfiguration runtimeConfiguration;
        try {
            runtimeConfiguration = (endpointRequirement == EndpointRequirement.ENABLED
                    ? endpointResolutionService.findEnabledRuntime(SYSTEM_CODE, environment, organizationId)
                    : endpointResolutionService.findConfiguredRuntime(SYSTEM_CODE, environment, organizationId))
                    .orElseThrow(() -> new PhisConfigurationException(
                            endpointRequirement == EndpointRequirement.ENABLED
                                    ? "当前机构没有通过校验的基层HIS接口配置"
                                    : "当前机构尚未保存可校验的基层HIS接口配置"));
        } catch (ResourceConflictException exception) {
            throw new PhisConfigurationException(exception.getMessage());
        }
        ExternalEndpoint endpoint = runtimeConfiguration.endpoint();
        if (endpoint.organizationId() == null || endpoint.organizationId() != organizationId) {
            throw new PhisConfigurationException("基层HIS接口配置与当前机构不匹配");
        }
        try {
            return PhisInvocationContext.create(
                    URI.create(endpoint.baseUrl()), endpoint.connectTimeoutMs(), endpoint.readTimeoutMs(),
                    resolveAuthorizationCode(runtimeConfiguration.authentication()));
        } catch (IllegalArgumentException exception) {
            throw new PhisConfigurationException("当前机构的基层HIS接口地址无效");
        }
    }

    /**
     * 输出不包含报文和凭证的交易完成日志。
     *
     * @param organizationId 机构主键
     * @param environment 部署环境
     * @param trade 已登记交易
     * @param response HIS业务响应
     * @param startedAt 调用开始时的单调时钟值
     */
    private void logCompletion(
            long organizationId,
            ParameterEnvironment environment,
            PhisTrade trade,
            PhisResponse<?> response,
            long startedAt
    ) {
        String outcome = response.success() ? "成功" : "业务失败";
        String result = outcome + "(" + response.resultCode() + ")";
        String template = "HIS交易｜{}｜机构{}/{}｜{}｜耗时{}";
        Object[] values = {tradeLabel(trade), organizationId,
                environmentName(environment), result, formatDuration(elapsedMillis(startedAt))};
        if (response.success()) {
            LOGGER.info(template, values);
        } else {
            LOGGER.warn(template, values);
        }
    }

    /**
     * 输出不包含地址、报文、凭证和底层异常栈的交易失败日志。
     *
     * @param organizationId 机构主键
     * @param environment 部署环境
     * @param trade 已登记交易
     * @param outcome 调用结果状态
     * @param reason 已脱敏原因
     * @param startedAt 调用开始时的单调时钟值
     */
    private void logFailure(
            long organizationId,
            ParameterEnvironment environment,
            PhisTrade trade,
            String outcome,
            String reason,
            long startedAt
    ) {
        LOGGER.warn("HIS交易｜{}｜机构{}/{}｜{}｜{}｜耗时{}",
                tradeLabel(trade), organizationId, environmentName(environment),
                outcome, reason, formatDuration(elapsedMillis(startedAt)));
    }

    /**
     * 生成只包含交易码和名称的受控日志标签。
     *
     * @param trade 已登记交易
     * @return 交易码和名称组成的日志标签
     */
    private String tradeLabel(PhisTrade trade) {
        return trade == null ? "未提供交易" : trade.code() + " " + trade.displayName();
    }

    /**
     * 把部署环境转换为简短中文名称。
     *
     * @param environment 部署环境
     * @return 简短中文环境名称
     */
    private String environmentName(ParameterEnvironment environment) {
        if (environment == null) {
            return "环境未知";
        }
        return environment == ParameterEnvironment.PRODUCTION ? "生产" : "测试";
    }

    /**
     * 根据单调时钟计算不小于零的调用耗时。
     *
     * @param startedAt 调用开始时的单调时钟值
     * @return 不小于零的耗时毫秒数
     */
    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    /**
     * 将耗时格式化为便于人工阅读的秒或分秒形式。
     *
     * @param durationMs 不小于零的耗时毫秒数
     * @return 一秒内为毫秒，一分钟内为秒，一分钟及以上为分秒
     */
    static String formatDuration(long durationMs) {
        long safeDurationMs = Math.max(0L, durationMs);
        if (safeDurationMs < 1_000L) {
            return safeDurationMs + "毫秒";
        }
        if (safeDurationMs >= 60_000L) {
            return safeDurationMs / 60_000L + "分" + safeDurationMs % 60_000L / 1_000L + "秒";
        }
        long seconds = safeDurationMs / 1_000L;
        long milliseconds = safeDurationMs % 1_000L;
        if (milliseconds == 0L) {
            return seconds + "秒";
        }
        String fraction = String.format("%03d", milliseconds).replaceFirst("0+$", "");
        return seconds + "." + fraction + "秒";
    }

    /**
     * 压缩并清理外部调用异常原因，避免重复状态文字和换行。
     *
     * @param reason 安全异常原因
     * @return 去除与结果状态重复文字后的简短原因
     */
    private String conciseReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "通信失败";
        }
        return reason.replace("，结果未知", "");
    }

    /**
     * 读取本次HIS调用使用的机构授权码。
     *
     * @param authentication 机构认证信息
     * @return 当前机构的授权码
     */
    private String resolveAuthorizationCode(ExternalEndpointAuthentication authentication) {
        if (authentication == null) {
            throw new PhisConfigurationException("当前机构未配置基层HIS认证信息");
        }
        return requireSecret(
                authentication.authorizationCode(), "当前机构未配置HIS授权码");
    }

    /**
     * 校验运行期认证字段存在且不为空白。
     *
     * @param value 认证字段
     * @param message 缺失提示
     * @return 非空认证字段
     */
    private String requireSecret(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PhisConfigurationException(message);
        }
        return value;
    }

    /** 限定业务调用允许解析的HIS端点状态。 */
    private enum EndpointRequirement {
        /** 只允许使用已经校验并启用的端点。 */
        ENABLED,

        /** 允许使用已经保存但尚未启用的待校验端点。 */
        CONFIGURED
    }
}
