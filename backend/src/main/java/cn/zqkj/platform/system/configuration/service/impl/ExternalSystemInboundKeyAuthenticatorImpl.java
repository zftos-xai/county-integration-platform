package cn.zqkj.platform.system.configuration.service.impl;

import cn.zqkj.platform.system.configuration.domain.model.ExternalSystemCaller;
import cn.zqkj.platform.system.configuration.mapper.ConfigurationMapper;
import cn.zqkj.platform.system.configuration.service.ExternalSystemInboundKeyAuthenticator;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 使用外部系统登记和Key单向校验值识别入站调用方。
 */
@Service
public class ExternalSystemInboundKeyAuthenticatorImpl implements ExternalSystemInboundKeyAuthenticator {

    private final ConfigurationMapper mapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建外部系统入站Key认证器。
     *
     * @param mapper 外部系统校验材料读取边界
     * @param passwordEncoder 单向Key校验编码器
     */
    public ExternalSystemInboundKeyAuthenticatorImpl(
            ConfigurationMapper mapper,
            PasswordEncoder passwordEncoder
    ) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 仅接受登记、启用且Key匹配的外部系统。
     *
     * @param systemCode 稳定外部系统代码
     * @param rawKey 调用方提供的明文Key
     * @return 认证成功的外部系统身份；认证失败时为空
     */
    @Override
    public Optional<ExternalSystemCaller> authenticate(String systemCode, String rawKey) {
        if (systemCode == null || systemCode.isBlank() || rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        String normalizedCode = systemCode.trim().toUpperCase(Locale.ROOT);
        return mapper.findExternalSystemInboundCredential(normalizedCode)
                .filter(credential -> credential.enabled() && passwordEncoder.matches(rawKey, credential.keyHash()))
                .map(credential -> new ExternalSystemCaller(credential.systemCode(), credential.systemName()));
    }
}
