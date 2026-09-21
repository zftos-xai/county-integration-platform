package cn.zqkj.platform.his.service.impl;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.his.domain.endpointverification.model.PhisEndpointVerificationResult;
import cn.zqkj.platform.his.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryType;
import cn.zqkj.platform.his.domain.organization.dto.OrganizationQuery;
import cn.zqkj.platform.his.domain.organization.model.OrganizationEntry;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.service.PhisEndpointVerificationService;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 通过100-008和100-003完成基层HIS端点的自动校验。
 */
@Service
public class PhisEndpointVerificationServiceImpl implements PhisEndpointVerificationService {

    private final PhisService phisService;

    /**
     * 创建基层HIS端点自动校验服务。
     *
     * @param phisService HIS交易调用入口
     */
    public PhisEndpointVerificationServiceImpl(PhisService phisService) {
        this.phisService = phisService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>通信、协议和配置异常不转换为失败结果，调用方必须按结果未知或配置不可用处理；只有HIS明确
     * 返回拒绝、无法唯一确认机构或机构编码变化才返回拒绝结论。</p>
     */
    @Override
    public PhisEndpointVerificationResult verify(
            long organizationId,
            ParameterEnvironment environment,
            String platformOrganizationName,
            String platformOrganizationCode,
            String previousSourceOrganizationId
    ) {
        PhisResponse<List<OrganizationEntry>> response = phisService.verifyOrganizationConfiguration(
                organizationId, environment, new OrganizationQuery(platformOrganizationName));
        if (!response.success()) {
            return PhisEndpointVerificationResult.rejected(
                    safeSummary(response.errorMessage(), "HIS明确拒绝了100-008机构查询"));
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
        var capability = phisService.verifyHospitalDirectoryCapability(
                organizationId, environment, new HospitalDirectoryQuery(
                        HospitalDirectoryType.DEPARTMENT, null,
                        Func.requireText(platformOrganizationCode, "平台机构编码", 64)));
        if (!capability.success()) {
            return PhisEndpointVerificationResult.rejected(
                    safeSummary(capability.errorMessage(), "HIS明确拒绝了100-003医院综合目录查询"));
        }
        return PhisEndpointVerificationResult.verified(sourceOrganizationId, Func.trimToNull(source.hospitalName()));
    }

    /**
     * 规范化可安全展示的HIS明确拒绝摘要。
     *
     * @param message HIS明确返回的摘要
     * @param fallback 没有摘要时的默认说明
     * @return 不超过500字的安全摘要
     */
    private String safeSummary(String message, String fallback) {
        String value = Func.trimToNull(message);
        if (value == null) {
            return fallback;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
