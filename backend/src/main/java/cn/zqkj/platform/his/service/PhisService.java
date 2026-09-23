package cn.zqkj.platform.his.service;

import cn.zqkj.platform.his.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.his.domain.icd10.dto.Icd10CountQuery;
import cn.zqkj.platform.his.domain.icd10.dto.Icd10Query;
import cn.zqkj.platform.his.domain.medicaldirectory.dto.MedicalDirectoryCountQuery;
import cn.zqkj.platform.his.domain.medicaldirectory.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.his.domain.organization.dto.OrganizationQuery;
import cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryEntry;
import cn.zqkj.platform.his.domain.icd10.model.Icd10Entry;
import cn.zqkj.platform.his.domain.medicaldirectory.model.MedicalDirectoryEntry;
import cn.zqkj.platform.his.domain.organization.model.OrganizationEntry;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisConfigurationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.his.exception.PhisRequestException;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;

import java.util.List;

/**
 * 按机构配置调用基层HIS的业务入口。
 *
 * <p>每个方法只执行一次外部请求，不自动重试。调用前配置或参数错误、通信状态未知、
 * 已接收响应不符合协议分别通过不同异常类型表达；HIS明确返回的业务失败保留在响应中。</p>
 */
public interface PhisService {

    /**
     * 查询基层HIS中的科室、医生、病区或床位目录。
     *
     * @param organizationId 机构主键
     * @param environment 部署环境
     * @param query 业务查询条件，不含授权码和交易码
     * @return 强类型的医院综合目录响应
     * @throws PhisConfigurationException 当前机构没有可用端点或认证配置时抛出
     * @throws PhisRequestException 查询条件不符合发送要求时抛出
     * @throws PhisCommunicationException 请求已发送但无法确认结果时抛出
     * @throws PhisProtocolException 已收到的响应不符合协议时抛出
     */
    PhisResponse<List<HospitalDirectoryEntry>> queryHospitalDirectory(
            long organizationId,
            ParameterEnvironment environment,
            HospitalDirectoryQuery query
    );

    /**
     * 分页查询指定约定时间范围的中药、西药、诊疗或耗材目录。
     *
     * @param organizationId 平台机构主键
     * @param environment 部署环境
     * @param query 强类型分页查询条件
     * @return 强类型目录响应
     * @throws PhisConfigurationException 当前机构没有可用端点或认证配置时抛出
     * @throws PhisRequestException 查询范围或分页条件不符合发送要求时抛出
     * @throws PhisCommunicationException 请求已发送但无法确认结果时抛出
     * @throws PhisProtocolException 已收到的响应不符合协议时抛出
     */
    PhisResponse<List<MedicalDirectoryEntry>> queryMedicalDirectory(
            long organizationId, ParameterEnvironment environment, MedicalDirectoryQuery query);

    /**
     * 查询与100-004分页完全相同范围的来源声明行数。
     *
     * @param organizationId 平台机构主键
     * @param environment 部署环境
     * @param query 强类型数量查询条件
     * @return 来源声明行数
     * @throws PhisConfigurationException 当前机构没有可用端点或认证配置时抛出
     * @throws PhisRequestException 查询范围不符合发送要求时抛出
     * @throws PhisCommunicationException 请求已发送但无法确认结果时抛出
     * @throws PhisProtocolException 已收到的响应不符合协议时抛出
     */
    PhisResponse<Long> countMedicalDirectory(
            long organizationId, ParameterEnvironment environment, MedicalDirectoryCountQuery query);

    /**
     * 分页查询平台公共ICD10目录的来源数据。
     *
     * <p>{@code endpointOrganizationId}仅用于解析获授权的调用端点，不能被解释为诊断目录记录的机构归属。</p>
     *
     * @param endpointOrganizationId 提供已验证HIS端点配置的机构主键
     * @param environment 部署环境
     * @param query 强类型分页查询条件
     * @return 强类型ICD10目录响应
     * @throws PhisConfigurationException 当前端点配置不可用时抛出
     * @throws PhisRequestException 查询范围或分页条件不符合接口文档时抛出
     * @throws PhisCommunicationException 请求已发送但无法确认结果时抛出
     * @throws PhisProtocolException 已收到的响应不符合协议时抛出
     */
    PhisResponse<List<Icd10Entry>> queryIcd10(
            long endpointOrganizationId, ParameterEnvironment environment, Icd10Query query);

    /**
     * 查询平台公共ICD10目录在指定范围内的来源声明行数。
     *
     * @param endpointOrganizationId 提供已验证HIS端点配置的机构主键，不是目录归属
     * @param environment 部署环境
     * @param query 与100-006完全一致的查询范围
     * @return 非负来源声明行数
     * @throws PhisConfigurationException 当前端点配置不可用时抛出
     * @throws PhisRequestException 查询范围不符合接口文档时抛出
     * @throws PhisCommunicationException 请求已发送但无法确认结果时抛出
     * @throws PhisProtocolException 已收到的响应不符合协议时抛出
     */
    PhisResponse<Long> countIcd10(
            long endpointOrganizationId, ParameterEnvironment environment, Icd10CountQuery query);

    /**
     * 使用已保存但尚未启用的机构配置验证100-003医院综合目录查询能力。
     *
     * <p>该调用只用于接口配置校验，不写入目录数据，也不能替代正式同步。</p>
     *
     * @param organizationId 机构主键
     * @param environment 部署环境
     * @param query 最小能力验证查询条件
     * @return HIS对100-003的明确业务响应
     * @throws PhisConfigurationException 当前机构没有已保存的待校验端点或认证配置时抛出
     * @throws PhisRequestException 查询条件不符合发送要求时抛出
     * @throws PhisCommunicationException 请求已发送但无法确认结果时抛出
     * @throws PhisProtocolException 已收到的响应不符合协议时抛出
     */
    PhisResponse<List<HospitalDirectoryEntry>> verifyHospitalDirectoryCapability(
            long organizationId,
            ParameterEnvironment environment,
            HospitalDirectoryQuery query
    );

    /**
     * 查询基层HIS来源医疗机构，供100-008机构映射核查使用。
     *
     * @param organizationId 平台机构主键
     * @param environment 部署环境
     * @param query 医院名称查询条件
     * @return 强类型来源机构响应
     * @throws PhisConfigurationException 当前机构没有可用端点或认证配置时抛出
     * @throws PhisRequestException 查询条件不符合发送要求时抛出
     * @throws PhisCommunicationException 请求已发送但无法确认结果时抛出
     * @throws PhisProtocolException 已收到的响应不符合协议时抛出
     */
    PhisResponse<List<OrganizationEntry>> queryOrganizations(
            long organizationId,
            ParameterEnvironment environment,
            OrganizationQuery query
    );

    /**
     * 使用已保存但尚未启用的机构配置执行100-008自动校验。
     *
     * <p>该调用只能用于确认来源机构，不能替代后续业务同步的可用端点解析。</p>
     *
     * @param organizationId 平台机构主键
     * @param environment 部署环境
     * @param query 医院名称查询条件
     * @return 强类型来源机构响应
     * @throws PhisConfigurationException 当前机构没有已保存的待校验端点或认证配置时抛出
     * @throws PhisRequestException 查询条件不符合发送要求时抛出
     * @throws PhisCommunicationException 请求已发送但无法确认结果时抛出
     * @throws PhisProtocolException 已收到的响应不符合协议时抛出
     */
    PhisResponse<List<OrganizationEntry>> verifyOrganizationConfiguration(
            long organizationId,
            ParameterEnvironment environment,
            OrganizationQuery query
    );
}
