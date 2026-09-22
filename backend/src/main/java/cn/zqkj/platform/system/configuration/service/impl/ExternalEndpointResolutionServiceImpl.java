package cn.zqkj.platform.system.configuration.service.impl;

import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointAuthentication;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointRuntimeConfiguration;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointScope;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.configuration.mapper.ConfigurationMapper;
import cn.zqkj.platform.system.configuration.service.ExternalEndpointResolutionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 使用配置域Mapper实现外部服务地址只读解析，避免其他业务域直接访问配置表。
 */
@Service
public class ExternalEndpointResolutionServiceImpl implements ExternalEndpointResolutionService {

    private static final String MANAGED_PREFIX = "managed://";
    private static final String ENVIRONMENT_PREFIX = "env://";
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");

    private final ConfigurationMapper configurationMapper;
    private final ExternalEndpointCredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;
    private final Function<String, String> environmentReader;

    /**
     * 创建外部服务地址解析服务。
     *
     * @param configurationMapper 配置域Mapper
     * @param credentialCipher 认证信息解密器
     * @param objectMapper JSON解析器
     */
    @Autowired
    public ExternalEndpointResolutionServiceImpl(
            ConfigurationMapper configurationMapper,
            ExternalEndpointCredentialCipher credentialCipher,
            ObjectMapper objectMapper
    ) {
        this(configurationMapper, credentialCipher, objectMapper, System::getenv);
    }

    /**
     * 创建可替换环境变量来源的配置解析服务。
     *
     * @param configurationMapper 配置域Mapper
     * @param credentialCipher 认证信息解密器
     * @param objectMapper JSON解析器
     * @param environmentReader 环境变量读取函数
     */
    ExternalEndpointResolutionServiceImpl(
            ConfigurationMapper configurationMapper,
            ExternalEndpointCredentialCipher credentialCipher,
            ObjectMapper objectMapper,
            Function<String, String> environmentReader
    ) {
        this.configurationMapper = configurationMapper;
        this.credentialCipher = credentialCipher;
        this.objectMapper = objectMapper;
        this.environmentReader = environmentReader;
    }

    /**
     * 解析已验证且启用的机构端点及其认证信息。
     *
     * <p>只解析已启用且验证通过的端点；解密后的认证信息仅存在于返回的运行时对象中，不写日志或数据库明文。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ExternalEndpointRuntimeConfiguration> findEnabledRuntime(
            String systemCode,
            ParameterEnvironment environment,
            long organizationId
    ) {
        return configurationMapper.findEnabledExternalEndpoint(systemCode, environment, organizationId)
                .map(endpoint -> new ExternalEndpointRuntimeConfiguration(
                        endpoint,
                        resolveAuthentication(endpoint)
                ));
    }

    /**
     * 解析机构已保存的端点及认证信息，供启用前验证使用。
     *
     * <p>用于启用前验证，可解析已配置端点；仍要求地址和受支持的凭证引用完整可用。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ExternalEndpointRuntimeConfiguration> findConfiguredRuntime(
            String systemCode,
            ParameterEnvironment environment,
            long organizationId
    ) {
        return configurationMapper.findConfiguredExternalEndpoint(systemCode, environment, organizationId)
                .map(endpoint -> new ExternalEndpointRuntimeConfiguration(endpoint, resolveAuthentication(endpoint)));
    }

    /**
     * 列出获准机构中端点与认证信息均可解析的可用环境。
     *
     * <p>先按机构权限取得候选范围，再过滤掉无法完整解析地址或认证信息的配置。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<ExternalEndpointScope> findAvailableScopes(
            String systemCode,
            List<String> organizationCodes
    ) {
        return configurationMapper.findEnabledExternalEndpointScopes(systemCode, organizationCodes).stream()
                .filter(scope -> hasResolvableRuntime(systemCode, scope))
                .toList();
    }

    /**
     * 判断端点是否满足业务运行时解析的全部前提。
     *
     * @param systemCode 外部系统代码
     * @param scope 接口适用范围
     * @return 地址和认证信息均可解析时为true
     */
    private boolean hasResolvableRuntime(String systemCode, ExternalEndpointScope scope) {
        try {
            return findEnabledRuntime(systemCode, scope.environment(), scope.organizationId()).isPresent();
        } catch (ResourceConflictException exception) {
            return false;
        }
    }

    /**
     * 解密托管凭证或解析受控兼容凭证引用。
     *
     * @param endpoint 接口配置
     * @return 解密后的认证信息
     */
    private ExternalEndpointAuthentication resolveAuthentication(ExternalEndpoint endpoint) {
        String reference = endpoint.credentialReference();
        if (reference != null && reference.startsWith(MANAGED_PREFIX)) {
            return credentialCipher.decrypt(configurationMapper.findExternalEndpointCredential(endpoint.id())
                    .orElseThrow(() -> new ResourceConflictException("当前机构尚未配置外部系统认证信息")));
        }
        if (reference == null || !reference.startsWith(ENVIRONMENT_PREFIX)) {
            throw new ResourceConflictException("外部系统认证信息存储类型不受支持");
        }
        String variableName = reference.substring(ENVIRONMENT_PREFIX.length());
        if (!VARIABLE_NAME.matcher(variableName).matches()) {
            throw new ResourceConflictException("外部系统认证信息存储格式无效");
        }
        String value = environmentReader.apply(variableName);
        if (Func.isBlank(value)) {
            throw new ResourceConflictException("当前机构的外部系统认证信息不可用");
        }
        try {
            ExternalEndpointAuthentication authentication = objectMapper.readValue(
                    value, ExternalEndpointAuthentication.class);
            if (Func.isBlank(authentication.vendorCode()) && Func.isBlank(authentication.username())
                    && Func.isBlank(authentication.password()) && Func.isBlank(authentication.authorizationCode())) {
                throw new ResourceConflictException("当前机构的外部系统认证信息格式无效");
            }
            return authentication;
        } catch (JsonProcessingException exception) {
            throw new ResourceConflictException("当前机构的外部系统认证信息格式无效");
        }
    }

}
