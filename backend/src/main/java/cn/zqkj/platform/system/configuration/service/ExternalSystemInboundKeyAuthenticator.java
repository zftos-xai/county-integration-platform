package cn.zqkj.platform.system.configuration.service;

import cn.zqkj.platform.system.configuration.domain.model.ExternalSystemCaller;
import java.util.Optional;

/**
 * 识别通过外部系统入站Key调用平台的系统。
 */
public interface ExternalSystemInboundKeyAuthenticator {

    /**
     * 校验外部系统代码、Key及系统启用状态。
     *
     * @param systemCode 稳定外部系统代码
     * @param rawKey 调用方提供的明文Key
     * @return 认证成功的外部系统身份；未登记、停用、未配置或Key错误时为空
     */
    Optional<ExternalSystemCaller> authenticate(String systemCode, String rawKey);
}
