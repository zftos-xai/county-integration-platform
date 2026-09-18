package cn.zqkj.platform.his.service.impl;

import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.his.client.PhisClient;
import cn.zqkj.platform.his.domain.model.PhisResponse;
import cn.zqkj.platform.his.domain.model.PhisTrade;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisConfigurationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.system.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.domain.model.ExternalEndpointAuthentication;
import cn.zqkj.platform.system.domain.model.ExternalEndpointRuntimeConfiguration;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.service.ExternalEndpointResolutionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;

/**
 * 解析机构配置、装配可信认证字段并通过SOAP适配器调用基层HIS。
 */
@Service
public class PhisServiceImpl implements PhisService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhisServiceImpl.class);

    /** 基层HIS在外部系统配置中的稳定代码。 */
    public static final String SYSTEM_CODE = "PRIMARY_HIS";

    private final ExternalEndpointResolutionService endpointResolutionService;
    private final PhisClient phisClient;
    private final ObjectMapper objectMapper;

    /**
     * @param endpointResolutionService 外部系统运行配置服务
     * @param phisClient SOAP协议适配器
     * @param objectMapper JSON编码器
     */
    public PhisServiceImpl(
            ExternalEndpointResolutionService endpointResolutionService,
            PhisClient phisClient,
            ObjectMapper objectMapper
    ) {
        this.endpointResolutionService = endpointResolutionService;
        this.phisClient = phisClient;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    public PhisResponse testConnection(long organizationId, ParameterEnvironment environment) {
        return invoke(organizationId, environment, "100-001", AuthenticationMode.VENDOR,
                objectMapper.createObjectNode());
    }

    /** {@inheritDoc} */
    @Override
    public PhisResponse login(long organizationId, ParameterEnvironment environment) {
        return invoke(organizationId, environment, "100-002", AuthenticationMode.LOGIN,
                objectMapper.createObjectNode());
    }

    /** {@inheritDoc} */
    @Override
    public PhisResponse invokeAuthorized(
            long organizationId,
            ParameterEnvironment environment,
            String tradeCode,
            ObjectNode parameters
    ) {
        if (tradeCode == null || !tradeCode.matches("\\d{3}-\\d{3}")) {
            throw new PhisConfigurationException("基层HIS交易码格式无效");
        }
        if (parameters == null) {
            throw new PhisConfigurationException("必须提供基层HIS业务参数");
        }
        return invoke(organizationId, environment, tradeCode, AuthenticationMode.AUTHORIZATION_CODE,
                parameters.deepCopy());
    }

    /**
     * 执行一次已确定认证方式的基层HIS调用。
     *
     * @param organizationId 机构主键
     * @param environment 部署环境
     * @param tradeCode 交易码
     * @param authenticationMode 认证方式
     * @param parameters 独立的业务参数副本
     * @return HIS业务响应
     */
    private PhisResponse invoke(
            long organizationId,
            ParameterEnvironment environment,
            String tradeCode,
            AuthenticationMode authenticationMode,
            ObjectNode parameters
    ) {
        long startedAt = System.nanoTime();
        PhisTrade trade = PhisTrade.findByCode(tradeCode).orElse(null);
        try {
            PhisResponse response = execute(
                    organizationId, environment, tradeCode, authenticationMode, parameters);
            logCompletion(organizationId, environment, tradeCode, trade, response, startedAt);
            return response;
        } catch (PhisConfigurationException exception) {
            logFailure(organizationId, environment, tradeCode, trade,
                    "未发送", exception.getMessage(), startedAt);
            throw exception;
        } catch (PhisCommunicationException exception) {
            logFailure(organizationId, environment, tradeCode, trade,
                    "结果未知", conciseReason(exception.getMessage()), startedAt);
            throw exception;
        } catch (PhisProtocolException exception) {
            logFailure(organizationId, environment, tradeCode, trade,
                    "结果未知", exception.getMessage(), startedAt);
            throw exception;
        } catch (RuntimeException exception) {
            logFailure(organizationId, environment, tradeCode, trade,
                    "状态不确定", "未分类运行错误", startedAt);
            throw exception;
        }
    }

    /**
     * 解析机构配置并执行目标调用，不承担运行日志输出。
     *
     * @param organizationId 机构主键
     * @param environment 部署环境
     * @param tradeCode 交易码
     * @param authenticationMode 认证方式
     * @param parameters 独立的业务参数副本
     * @return HIS业务响应
     */
    private PhisResponse execute(
            long organizationId,
            ParameterEnvironment environment,
            String tradeCode,
            AuthenticationMode authenticationMode,
            ObjectNode parameters
    ) {
        if (organizationId <= 0 || environment == null) {
            throw new PhisConfigurationException("必须提供有效机构和部署环境");
        }
        ExternalEndpointRuntimeConfiguration runtimeConfiguration;
        try {
            runtimeConfiguration = endpointResolutionService.findEnabledRuntime(
                            SYSTEM_CODE, environment, organizationId)
                    .orElseThrow(() -> new PhisConfigurationException("当前机构未配置启用的基层HIS接口"));
        } catch (ResourceConflictException exception) {
            throw new PhisConfigurationException(exception.getMessage());
        }
        ExternalEndpoint endpoint = runtimeConfiguration.endpoint();
        if (endpoint.organizationId() == null || endpoint.organizationId() != organizationId) {
            throw new PhisConfigurationException("基层HIS接口配置与当前机构不匹配");
        }
        applyAuthentication(parameters, authenticationMode, runtimeConfiguration.authentication());
        try {
            return phisClient.invoke(
                    URI.create(endpoint.baseUrl()),
                    endpoint.connectTimeoutMs(),
                    endpoint.readTimeoutMs(),
                    tradeCode,
                    objectMapper.writeValueAsString(parameters)
            );
        } catch (IllegalArgumentException exception) {
            throw new PhisConfigurationException("当前机构的基层HIS接口地址无效");
        } catch (JsonProcessingException exception) {
            throw new PhisConfigurationException("基层HIS业务参数编码失败");
        }
    }

    /**
     * 输出不包含报文和凭证的交易完成日志。
     *
     * @param organizationId 机构主键
     * @param environment 部署环境
     * @param tradeCode 交易码
     * @param trade 已登记交易定义；未登记时为空
     * @param response HIS业务响应
     * @param startedAt 调用开始时的单调时钟值
     */
    private void logCompletion(
            long organizationId,
            ParameterEnvironment environment,
            String tradeCode,
            PhisTrade trade,
            PhisResponse response,
            long startedAt
    ) {
        String outcome = response.success() ? "成功" : "业务失败";
        String result = outcome + "(" + response.resultCode() + ")";
        String template = "HIS交易｜{}｜机构{}/{}｜{}｜耗时{}";
        Object[] values = {tradeLabel(tradeCode, trade), organizationId,
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
     * @param tradeCode 交易码
     * @param trade 已登记交易定义；未登记时为空
     * @param outcome 调用结果状态
     * @param reason 已脱敏原因
     * @param startedAt 调用开始时的单调时钟值
     */
    private void logFailure(
            long organizationId,
            ParameterEnvironment environment,
            String tradeCode,
            PhisTrade trade,
            String outcome,
            String reason,
            long startedAt
    ) {
        LOGGER.warn("HIS交易｜{}｜机构{}/{}｜{}｜{}｜耗时{}",
                tradeLabel(tradeCode, trade), organizationId, environmentName(environment),
                outcome, reason, formatDuration(elapsedMillis(startedAt)));
    }

    /** @param tradeCode 交易码 @param trade 交易定义 @return 交易码和名称组成的日志标签 */
    private String tradeLabel(String tradeCode, PhisTrade trade) {
        return tradeCode + " " + (trade == null ? "未登记交易" : trade.displayName());
    }

    /** @param environment 部署环境 @return 简短中文环境名称 */
    private String environmentName(ParameterEnvironment environment) {
        if (environment == null) {
            return "环境未知";
        }
        return environment == ParameterEnvironment.PRODUCTION ? "生产" : "测试";
    }

    /** @param startedAt 调用开始时的单调时钟值 @return 不小于零的耗时毫秒数 */
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

    /** @param reason 安全异常原因 @return 去除与结果状态重复文字后的简短原因 */
    private String conciseReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "通信失败";
        }
        return reason.replace("，结果未知", "");
    }

    /** @param parameters 业务参数 @param mode 认证方式 @param authentication 机构认证信息 */
    private void applyAuthentication(
            ObjectNode parameters,
            AuthenticationMode mode,
            ExternalEndpointAuthentication authentication
    ) {
        if (authentication == null) {
            throw new PhisConfigurationException("当前机构未配置基层HIS认证信息");
        }
        switch (mode) {
            case VENDOR -> parameters.put("厂商编号", requireSecret(
                    authentication.vendorCode(), "当前机构未配置厂商编号"));
            case LOGIN -> {
                parameters.put("用户名", requireSecret(authentication.username(), "当前机构未配置HIS用户名"));
                parameters.put("密码", requireSecret(authentication.password(), "当前机构未配置HIS密码"));
                parameters.put("厂商编号", requireSecret(
                        authentication.vendorCode(), "当前机构未配置厂商编号"));
            }
            case AUTHORIZATION_CODE -> parameters.put("验证码", requireSecret(
                    authentication.authorizationCode(), "当前机构未配置HIS授权码"));
        }
    }

    /** @param value 认证字段 @param message 缺失提示 @return 非空认证字段 */
    private String requireSecret(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PhisConfigurationException(message);
        }
        return value;
    }

    /** 当前已确认接口使用的认证字段组合。 */
    private enum AuthenticationMode {
        /** 厂商编号。 */
        VENDOR,
        /** 用户名、密码和厂商编号。 */
        LOGIN,
        /** 机构授权码。 */
        AUTHORIZATION_CODE
    }
}
