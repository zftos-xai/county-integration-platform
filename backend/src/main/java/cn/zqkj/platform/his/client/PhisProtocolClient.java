package cn.zqkj.platform.his.client;

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
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.his.exception.PhisRequestException;

import java.util.List;

/**
 * 调用基层HIS {@code PHIS_Interface}的协议适配边界。
 *
 * <p>调用方必须先解析机构端点及认证信息并创建不可变调用上下文；实现负责请求参数校验、
 * 协议传输、报文转换与响应校验。所有方法只执行一次请求，不自动重试。</p>
 */
public interface PhisProtocolClient {

    /**
     * 查询基层HIS医院综合目录。
     *
     * @param context 已校验且包含机构认证信息的调用上下文
     * @param query 强类型查询条件
     * @return 成功时包含不可变目录列表；HIS明确拒绝时返回不含数据的业务失败响应
     * @throws PhisRequestException 请求参数在发送前不符合要求时抛出
     * @throws PhisCommunicationException HTTP、超时、中断或网络通信失败时抛出
     * @throws PhisProtocolException 已收到的SOAP或业务响应不符合协议时抛出
     */
    PhisResponse<List<HospitalDirectoryEntry>> queryHospitalDirectory(
            PhisInvocationContext context,
            HospitalDirectoryQuery query
    );

    /**
     * 分页查询药品、诊疗或耗材目录。
     *
     * @param context 已校验且包含机构认证信息的调用上下文
     * @param query 与行数查询完全一致的范围及分页条件
     * @return 成功时包含不可变目录列表；HIS明确拒绝时返回不含数据的业务失败响应
     * @throws PhisRequestException 请求参数在发送前不符合要求时抛出
     * @throws PhisCommunicationException HTTP、超时、中断或网络通信失败时抛出
     * @throws PhisProtocolException 已收到的SOAP或业务响应不符合协议时抛出
     */
    PhisResponse<List<MedicalDirectoryEntry>> queryMedicalDirectory(
            PhisInvocationContext context, MedicalDirectoryQuery query);

    /**
     * 查询药品、诊疗或耗材目录在指定范围内的声明行数。
     *
     * @param context 已校验且包含机构认证信息的调用上下文
     * @param query 数量查询范围
     * @return 成功时包含非负声明行数；HIS明确拒绝时返回不含数据的业务失败响应
     * @throws PhisRequestException 请求参数在发送前不符合要求时抛出
     * @throws PhisCommunicationException HTTP、超时、中断或网络通信失败时抛出
     * @throws PhisProtocolException 已收到的SOAP或业务响应不符合协议时抛出
     */
    PhisResponse<Long> countMedicalDirectory(
            PhisInvocationContext context, MedicalDirectoryCountQuery query);

    /**
     * 分页查询ICD10诊断目录。
     *
     * <p>调用只使用端点认证和显式查询条件；请求不携带机构归属字段，诊断版本仅在调用方已取得来源事实时传入。</p>
     *
     * @param context 已校验且包含端点认证信息的调用上下文
     * @param query 与行数查询完全一致的范围及分页条件
     * @return 成功时包含不可变诊断列表；HIS明确拒绝时返回不含数据的业务失败响应
     * @throws PhisRequestException 请求参数在发送前不符合协议时抛出
     * @throws PhisCommunicationException HTTP、超时、中断或网络通信失败时抛出
     * @throws PhisProtocolException 已收到的SOAP或业务响应不符合协议时抛出
     */
    PhisResponse<List<Icd10Entry>> queryIcd10(PhisInvocationContext context, Icd10Query query);

    /**
     * 查询ICD10诊断目录在指定范围内的声明行数。
     *
     * @param context 已校验且包含端点认证信息的调用上下文
     * @param query 与100-006完全一致的名称、时间、类别和可选版本条件
     * @return 成功时包含非负声明行数；HIS明确拒绝时返回不含数据的业务失败响应
     * @throws PhisRequestException 请求参数在发送前不符合协议时抛出
     * @throws PhisCommunicationException HTTP、超时、中断或网络通信失败时抛出
     * @throws PhisProtocolException 已收到的SOAP或业务响应不符合协议时抛出
     */
    PhisResponse<Long> countIcd10(PhisInvocationContext context, Icd10CountQuery query);

    /**
     * 查询基层HIS医疗机构信息。
     *
     * @param context 已校验且包含机构认证信息的调用上下文
     * @param query 强类型查询条件
     * @return 成功时包含不可变机构列表；HIS明确拒绝时返回不含数据的业务失败响应
     * @throws PhisRequestException 请求参数在发送前不符合要求时抛出
     * @throws PhisCommunicationException HTTP、超时、中断或网络通信失败时抛出
     * @throws PhisProtocolException 已收到的SOAP或业务响应不符合协议时抛出
     */
    PhisResponse<List<OrganizationEntry>> queryOrganizations(
            PhisInvocationContext context,
            OrganizationQuery query
    );
}
