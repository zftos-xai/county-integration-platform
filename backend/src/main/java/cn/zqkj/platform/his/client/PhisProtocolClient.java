package cn.zqkj.platform.his.client;

import cn.zqkj.platform.his.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.his.domain.icd10.dto.Icd10CountQuery;
import cn.zqkj.platform.his.domain.icd10.dto.Icd10Query;
import cn.zqkj.platform.his.domain.lis.dto.LisItemQuery;
import cn.zqkj.platform.his.domain.lis.dto.LisApplicationQuery;
import cn.zqkj.platform.his.domain.lis.dto.LisReportWrite;
import cn.zqkj.platform.his.domain.lis.model.LisItemEntry;
import cn.zqkj.platform.his.domain.lis.model.LisApplicationEntry;
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
     * 查询基层 HIS 指定机构的 LIS 项目包和明细。
     *
     * @param context 已校验且包含机构认证信息的调用上下文
     * @param query 项目目录查询条件
     * @return 成功时包含来源项目包明细；HIS明确拒绝时返回不含数据的业务失败响应
     * @throws PhisRequestException 请求参数在发送前不符合要求时抛出
     * @throws PhisCommunicationException HTTP、超时、中断或网络通信失败时抛出
     * @throws PhisProtocolException 已收到的SOAP或业务响应不符合协议时抛出
     */
    PhisResponse<List<LisItemEntry>> queryLisItems(PhisInvocationContext context, LisItemQuery query);

    /**
     * 按指定申请标识查询基层 HIS 中的 LIS 申请。
     *
     * @param context 已校验且包含机构认证信息的调用上下文
     * @param query 至少一种申请查询标识；目标HIS机构从调用上下文取得
     * @return 成功时包含仅在本次调用内传递的申请、患者和就诊信息
     * @throws PhisRequestException 请求参数在发送前不符合要求时抛出
     * @throws PhisCommunicationException HTTP、超时、中断或网络通信失败时抛出
     * @throws PhisProtocolException 已收到的SOAP或业务响应不符合协议时抛出
     */
    PhisResponse<List<LisApplicationEntry>> queryLisApplication(
            PhisInvocationContext context, LisApplicationQuery query);

    /**
     * 将调用方已编码的FHIR检验报告提交给基层HIS。
     *
     * <p>该方法只执行一次写请求，不自动重试；通信异常表示目标保存状态未知。</p>
     *
     * @param context 已校验且包含机构认证信息的调用上下文
     * @param query 已编码报告字符串
     * @return HIS明确成功时返回其消息文本；明确拒绝时返回业务失败响应
     * @throws PhisRequestException 报告内容在发送前不符合要求时抛出
     * @throws PhisCommunicationException 请求已发送但无法确认保存结果时抛出
     * @throws PhisProtocolException 已收到的SOAP或业务响应不符合协议时抛出
     */
    PhisResponse<String> writeLisReport(PhisInvocationContext context, LisReportWrite query);

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
