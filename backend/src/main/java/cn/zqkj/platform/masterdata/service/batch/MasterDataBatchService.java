package cn.zqkj.platform.masterdata.service.batch;

import cn.zqkj.platform.masterdata.domain.batch.dto.MasterDataBatchQuery;
import cn.zqkj.platform.masterdata.domain.batch.dto.StartMasterDataBatchRequest;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchPageVO;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataSyncOptionsVO;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectorySyncResultVO;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectorySyncResultVO;
import cn.zqkj.platform.masterdata.domain.icd10.vo.Icd10SyncResultVO;
import cn.zqkj.platform.masterdata.domain.icd10.dto.Icd10HisInvocationQuery;
import cn.zqkj.platform.masterdata.domain.icd10.vo.Icd10HisInvocationPageVO;
import cn.zqkj.platform.exchange.domain.vo.ExchangeRecordVO;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import java.util.List;

/**
 * 定义基础数据同步批次的创建、受控取消和查询用例。
 */
public interface MasterDataBatchService {

    /**
     * 以现有分项事实结束中断批次并撤销旧执行权，不重新调用HIS。
     * @param id 批次主键，仍按操作人机构范围读取
     * @param expectedVersion 当前批次行版本
     * @param actor 通过入口功能授权的操作人
     * @return 已结束批次；缺少分项时为部分结果未知
     */
    MasterDataBatchSummaryVO recover(long id, byte[] expectedVersion, AccessActor actor);

    /**
     * 占用机构范围内的批次并执行对应目录同步，返回本轮已保存的处理结果。
     *
     * @param id 批次主键
     * @param expectedVersion 入口解码的八字节行版本；仅用于原子抢占
     * @param actor 入口确认的机构范围及审计身份
     * @return 完成、部分失败或部分结果未知的批次摘要
     */
    MasterDataBatchSummaryVO run(long id, byte[] expectedVersion, AccessActor actor);

    /**
     * 按调用入口确认的机构范围分页查询同步批次。
     *
     * @param query 入口已校验的查询条件；时间由持久化访问器转换为 UTC
     * @param allowedOrganizationCodes 已在调用入口确认的机构范围；空集合仍可查询平台级批次
     * @return 平台级及给定机构范围内的批次分页结果
     */
    MasterDataBatchPageVO findPage(MasterDataBatchQuery query, List<String> allowedOrganizationCodes);

    /**
     * 按主键读取基础数据同步批次；不存在时由调用边界按约定处理。
     *
     * @param id 批次主键
     * @param allowedOrganizationCodes 调用入口确认的机构范围；空集合仅可读取平台级批次
     * @return 范围内的批次摘要；不存在或超出范围时抛出资源不存在异常
     */
    MasterDataBatchSummaryVO get(long id, List<String> allowedOrganizationCodes);

    /**
     * 读取100-003批次按科室、医生、病区和床位保存的分项处理事实。
     *
     * @param id 同步批次主键
     * @param allowedOrganizationCodes 调用入口确认的机构范围
     * @return 分项结果；历史批次未保存分项事实时返回空列表
     */
    List<HospitalDirectorySyncResultVO> findHospitalDirectoryResults(long id, List<String> allowedOrganizationCodes);

    /**
     * 读取100-004/100-005批次按中药、西药、诊疗和耗材保存的分项处理事实。
     *
     * @param id 同步批次主键
     * @param allowedOrganizationCodes 调用入口确认的机构范围
     * @return 分项结果；未执行批次返回空列表
     */
    List<MedicalDirectorySyncResultVO> findMedicalDirectoryResults(long id, List<String> allowedOrganizationCodes);

    /**
     * 读取公共ICD10批次按西医、中医类别保存的分项运行结果。
     *
     * @param id 平台范围ICD10批次主键
     * @param allowedOrganizationCodes 调用入口确认的机构范围；平台批次不要求目录机构归属
     * @return 分项结果；未执行批次返回空列表
     */
    List<Icd10SyncResultVO> findIcd10Results(long id, List<String> allowedOrganizationCodes);

    /**
     * 分页读取公共ICD10批次的已落库HIS调用事实。
     *
     * <p>读取不会重发100-006或100-007；历史批次没有追踪记录时返回空页，不能据此推断HIS未被调用。</p>
     *
     * @param id 平台范围ICD10批次主键
     * @param query 服务端已校验的有界分页条件
     * @param allowedOrganizationCodes 调用入口确认的机构范围；仅用于确认批次可见性
     * @return 不含完整报文和凭证的调用事实页
     */
    Icd10HisInvocationPageVO findIcd10HisInvocations(
            long id, Icd10HisInvocationQuery query, List<String> allowedOrganizationCodes);

    /**
     * 读取机构目录批次已保存的通用HIS调用事实。
     *
     * <p>仅适用于100-003、100-004和100-005；调用事实按批次号与机构代码精确关联，
     * 不调用HIS，不返回完整报文或认证信息。公共ICD10须读取其专用调用事实。</p>
     *
     * @param id 同步批次主键
     * @param allowedOrganizationCodes 调用入口确认的机构范围
     * @return 最多一百条按请求时间倒序排列的脱敏调用事实
     */
    List<ExchangeRecordVO> findBatchHisInvocations(long id, List<String> allowedOrganizationCodes);

    /**
     * 查询给定机构范围内实际可用的 HIS 来源和同步业务。
     *
     * @param allowedOrganizationCodes 调用入口确认的机构范围
     * @return 真实来源和已实现闭环的同步业务
     */
    MasterDataSyncOptionsVO findSyncOptions(List<String> allowedOrganizationCodes);

    /**
     * 创建尚未执行的基础数据同步批次。
     *
     * @param request 同步请求
     * @param actor 当前操作人
     * @return 新建但尚未执行的批次
     */
    MasterDataBatchSummaryVO start(StartMasterDataBatchRequest request, AccessActor actor);

    /**
     * 在执行开始前取消同步批次。
     *
     * @param id 批次主键
     * @param expectedVersion 最近读取的并发版本
     * @param actor 当前操作人
     * @return 已取消批次
     */
    MasterDataBatchSummaryVO cancel(long id, byte[] expectedVersion, AccessActor actor);
}
