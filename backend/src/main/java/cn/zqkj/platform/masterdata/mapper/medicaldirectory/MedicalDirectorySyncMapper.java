package cn.zqkj.platform.masterdata.mapper.medicaldirectory;

import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCounts;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectorySyncResultVO;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 读写100-004医疗目录的当前有效数据和按类型运行事实。
 */
@Mapper
public interface MedicalDirectorySyncMapper {

    /**
     * 分类同一稳定来源键的当前目录状态。
     *
     * @param organizationId 平台机构主键
     * @param record 完整取得且校验通过的来源记录
     * @return 0新增、1更新或恢复有效、2未变化
     */
    int classifyDirectory(@Param("organizationId") long organizationId,
                          @Param("record") MedicalDirectorySourceRecord record);

    /**
     * 更新已存在的当前医疗目录记录。
     *
     * @param batchId 同步批次主键
     * @param organizationId 平台机构主键
     * @param record 已校验来源记录
     * @return 实际更新行数；不是1时调用方回滚当前类型，不能计为已更新
     */
    int updateDirectoryDirect(@Param("batchId") long batchId, @Param("organizationId") long organizationId,
                               @Param("record") MedicalDirectorySourceRecord record);

    /**
     * 插入首次出现的当前医疗目录记录。
     *
     * @param batchId 同步批次主键
     * @param organizationId 平台机构主键
     * @param record 已校验来源记录
     * @return 实际插入行数；并发情况下发现同一稳定键已存在时为0
     */
    int insertDirectoryDirect(@Param("batchId") long batchId, @Param("organizationId") long organizationId,
                              @Param("record") MedicalDirectorySourceRecord record);

    /**
     * 统计指定机构所有医疗目录的当前有效记录数。
     *
     * @param organizationId 平台机构主键
     * @return 当前有效记录数
     */
    long countActive(@Param("organizationId") long organizationId);

    /**
     * 统计指定机构和医疗目录类型的当前有效记录数。
     *
     * @param organizationId 平台机构主键
     * @param directoryType 医疗目录类型
     * @return 当前有效记录数
     */
    long countActiveByType(@Param("organizationId") long organizationId,
                           @Param("directoryType") MedicalDirectoryType directoryType);

    /**
     * 保存单一医疗目录类型本轮同步的事实结果。
     *
     * @param batchId 同步批次主键
     * @param result 单一目录类型结果
     */
    void saveDirectoryResult(@Param("batchId") long batchId,
                             @Param("result") MedicalDirectorySyncResultVO result);

    /**
     * 读取一个100-004/100-005批次按医疗目录类型持久化的运行事实。
     *
     * @param batchId 同步批次主键
     * @return 按HIS目录类型编码升序的分项结果
     */
    List<MedicalDirectorySyncResultVO> findResults(@Param("batchId") long batchId);

    /**
     * 保存本批次完成状态和汇总事实；必须在持有批次执行版本锁的短事务内调用。
     *
     * @param batchId 同步批次主键
     * @param status 完成或部分失败状态
     * @param counts 已持久化分项的汇总数量
     * @param failureCode 可选受控失败代码
     * @param failureSummary 可选受控摘要
     * @param actor 审计操作人
     * @return 状态匹配时为1，否则为0
     */
    int finishCompleted(@Param("batchId") long batchId, @Param("status") String status,
                        @Param("counts") MasterDataBatchCounts counts,
                        @Param("failureCode") String failureCode,
                        @Param("failureSummary") String failureSummary, @Param("actor") String actor);
}
