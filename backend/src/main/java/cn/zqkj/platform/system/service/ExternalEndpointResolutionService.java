package cn.zqkj.platform.system.service;

import cn.zqkj.platform.system.domain.model.ExternalEndpointRuntimeConfiguration;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;

import java.util.Optional;

/**
 * 向业务域提供只读的外部系统服务地址解析边界。
 */
public interface ExternalEndpointResolutionService {

    /**
     * 解析当前机构调用外部系统所需的地址和认证信息。
     *
     * @param systemCode 外部系统稳定代码
     * @param environment 部署环境
     * @param organizationId 机构主键
     * @return 完整运行配置；没有启用配置时为空
     */
    Optional<ExternalEndpointRuntimeConfiguration> findEnabledRuntime(
            String systemCode,
            ParameterEnvironment environment,
            long organizationId
    );
}
