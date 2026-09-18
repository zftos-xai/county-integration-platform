package cn.zqkj.platform.system.service.impl;

import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.system.domain.model.ExternalEndpointAuthentication;
import cn.zqkj.platform.system.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.domain.model.ExternalEndpointRuntimeConfiguration;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.mapper.ConfigurationMapper;
import cn.zqkj.platform.system.service.ExternalEndpointResolutionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

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

    /** {@inheritDoc} */
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

    /** @param endpoint 接口配置 @return 解密后的认证信息 */
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
        if (value == null || value.isBlank()) {
            throw new ResourceConflictException("当前机构的外部系统认证信息不可用");
        }
        try {
            ExternalEndpointAuthentication authentication = objectMapper.readValue(
                    value, ExternalEndpointAuthentication.class);
            if (isBlank(authentication.vendorCode()) && isBlank(authentication.username())
                    && isBlank(authentication.password()) && isBlank(authentication.authorizationCode())) {
                throw new ResourceConflictException("当前机构的外部系统认证信息格式无效");
            }
            return authentication;
        } catch (JsonProcessingException exception) {
            throw new ResourceConflictException("当前机构的外部系统认证信息格式无效");
        }
    }

    /** @param value 认证字段 @return 是否为空 */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
