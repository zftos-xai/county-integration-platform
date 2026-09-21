package cn.zqkj.platform.masterdata.service.batch;

import cn.zqkj.platform.masterdata.domain.batch.dto.MasterDataBatchQuery;
import cn.zqkj.platform.masterdata.domain.batch.dto.StartMasterDataBatchRequest;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchPageVO;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectorySyncResultVO;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectorySyncResultVO;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataSyncOptionsVO;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;

import java.util.List;


/**
 * 定义基础数据同步批次的创建、受控取消和查询用例。
 */
public interface MasterDataBatchService {

    /**
     * 按当前操作人的机构范围分页查询同步批次。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 有权限查看的批次分页结果
     */
    MasterDataBatchPageVO findPage(MasterDataBatchQuery query, AccessActor actor);

    /**
     * 按主键读取基础数据同步批次；不存在时由调用边界按约定处理。
     *
     * @param id 批次主键
     * @param actor 当前操作人
     * @return 有权限查看的批次摘要
     */
    MasterDataBatchSummaryVO get(long id, AccessActor actor);

    /**
     * 读取100-003批次按科室、医生、病区和床位保存的分项处理事实。
     *
     * @param id 同步批次主键
     * @param actor 当前操作人
     * @return 分项结果；历史批次未保存分项事实时返回空列表
     */
    List<HospitalDirectorySyncResultVO> findHospitalDirectoryResults(long id, AccessActor actor);

    /**
     * 读取100-004/100-005批次按中药、西药、诊疗和耗材保存的分项处理事实。
     *
     * @param id 同步批次主键
     * @param actor 当前操作人
     * @return 分项结果；未执行批次返回空列表
     */
    List<MedicalDirectorySyncResultVO> findMedicalDirectoryResults(long id, AccessActor actor);

    /**
     * 查询当前操作人实际可用的HIS来源和同步业务。
     *
     * @param actor 当前操作人
     * @return 真实来源和已实现闭环的同步业务
     */
    MasterDataSyncOptionsVO findSyncOptions(AccessActor actor);

    /**
     * 创建尚未执行的基础数据同步批次。
     *
     * @param request 同步请求
     * @param actor 当前操作人
     * @return 新建但尚未执行的批次
     */
    MasterDataBatchSummaryVO start(StartMasterDataBatchRequest request, AccessActor actor);

    /**
     * 在执行开始前取消维护方案。
     *
     * @param id 批次主键
     * @param expectedVersion 最近读取的并发版本
     * @param actor 当前操作人
     * @return 已取消批次
     */
    MasterDataBatchSummaryVO cancel(long id, byte[] expectedVersion, AccessActor actor);
}
