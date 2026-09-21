package cn.zqkj.platform.system.service;

import cn.zqkj.platform.system.domain.model.ExternalEndpointRuntimeConfiguration;
import cn.zqkj.platform.system.domain.model.ExternalEndpointScope;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;

import java.util.List;
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

    /**
     * 解析已保存的机构端点以执行100-008自动校验。
     *
     * <p>该方法不会将端点视作已经可用于业务同步；调用完成前，端点仍必须保持未启用。</p>
     *
     * @param systemCode 外部系统稳定代码
     * @param environment 部署环境
     * @param organizationId 平台机构主键
     * @return 已保存且凭证可解析的端点；不存在时为空
     */
    Optional<ExternalEndpointRuntimeConfiguration> findConfiguredRuntime(
            String systemCode,
            ParameterEnvironment environment,
            long organizationId
    );

    /**
     * 查询当前机构范围内可以实际解析运行配置的接口范围。
     *
     * @param systemCode 外部系统稳定代码
     * @param organizationCodes 当前操作人可访问的机构代码
     * @return 不含服务地址和认证信息的可用接口范围
     */
    List<ExternalEndpointScope> findAvailableScopes(String systemCode, List<String> organizationCodes);
}
