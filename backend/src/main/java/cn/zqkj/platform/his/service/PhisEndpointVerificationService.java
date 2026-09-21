package cn.zqkj.platform.his.service;

import cn.zqkj.platform.his.domain.endpointverification.model.PhisEndpointVerificationResult;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;

/**
 * 执行基层HIS端点的100-008来源确认与100-003读取能力校验。
 */
public interface PhisEndpointVerificationService {

    /**
     * 在不启用端点的前提下校验其来源机构与最小目录读取能力。
     *
     * @param organizationId 平台机构主键
     * @param environment 接口运行环境
     * @param platformOrganizationName 平台机构名称，用作100-008查询条件
     * @param platformOrganizationCode 平台机构稳定编码，用作100-003机构范围
     * @param previousSourceOrganizationId 历史已确认来源机构编码；空值表示首次校验
     * @return 明确通过或明确拒绝的校验结论
     */
    PhisEndpointVerificationResult verify(
            long organizationId,
            ParameterEnvironment environment,
            String platformOrganizationName,
            String platformOrganizationCode,
            String previousSourceOrganizationId
    );
}
