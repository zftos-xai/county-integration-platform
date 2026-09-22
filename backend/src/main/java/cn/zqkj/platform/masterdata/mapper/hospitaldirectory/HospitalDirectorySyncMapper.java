package cn.zqkj.platform.masterdata.mapper.hospitaldirectory;

import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCounts;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryRelationRecord;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectorySyncResultVO;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryType;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 读写100-003医院综合目录的当前有效数据和同步运行事实。
 *
 * <p>每个目录类型在完整取得并通过自动校验后独立对账；不使用人工发布或版本切换。</p>
 */
@Mapper
public interface HospitalDirectorySyncMapper {

    /**
     * 在写入前判断同一稳定来源键的当前记录状态。
     *
     * @param organizationId 平台机构主键
     * @param record 完整取得且已校验的来源记录
     * @return 0为新增、1为更新或恢复有效、2为内容未变
     */
    int classifyDirectory(@Param("organizationId") long organizationId,
                          @Param("record") HospitalDirectorySourceRecord record);

    /**
     * 更新已存在的正式目录内容，并记录本次成功同步批次。
     *
     * @param batchId 同步运行主键
     * @param organizationId 平台机构主键
     * @param record 已校验来源记录
     */
    void updateDirectoryDirect(@Param("batchId") long batchId, @Param("organizationId") long organizationId,
                               @Param("record") HospitalDirectorySourceRecord record);

    /**
     * 插入一条经过完整校验的正式医院目录记录。
     *
     * @param batchId 同步运行主键
     * @param organizationId 平台机构主键
     * @param record 已校验来源记录
     */
    void insertDirectoryDirect(@Param("batchId") long batchId, @Param("organizationId") long organizationId,
                               @Param("record") HospitalDirectorySourceRecord record);

    /**
     * 将指定机构和目录类型中本次HIS未返回的当前记录标记为无效。
     *
     * <p>只能在该类型已完整取得并通过自动校验后调用，避免将漏页或结果未知误判为HIS删除。</p>
     *
     * @param batchId 已完整取得并通过自动校验的同步运行主键
     * @param organizationId 平台机构主键
     * @param directoryType 本次已完整同步的目录类型
     * @return 本次从有效改为无效的记录数
     */
    int markMissingDirectoryInvalid(@Param("batchId") long batchId, @Param("organizationId") long organizationId,
                                    @Param("directoryType") HospitalDirectoryType directoryType);

    /**
     * 在重建关系前删除本批次目标类型的旧关系。
     *
     * @param organizationId 平台机构主键
     * @param directoryType 已完整同步的目录类型
     */
    void deleteRelationsForType(@Param("organizationId") long organizationId,
                                @Param("directoryType") HospitalDirectoryType directoryType);

    /**
     * 插入一条经过校验且目标目录已同步的目录关系。
     *
     * @param batchId 同步运行主键
     * @param organizationId 平台机构主键
     * @param relation 已校验的同类型关系
     */
    void insertRelationDirect(@Param("batchId") long batchId, @Param("organizationId") long organizationId,
                              @Param("relation") HospitalDirectoryRelationRecord relation);

    /**
     * 统计指定机构的当前有效医院综合目录数。
     *
     * @param organizationId 平台机构主键
     * @return {@code dbo.md_hospital_directory}中 {@code is_active = 1}的记录数
     */
    long countActive(@Param("organizationId") long organizationId);

    /**
     * 统计指定机构及目录类型处理后的当前有效记录数。
     *
     * @param organizationId 平台机构主键
     * @param directoryType 已完成直接对账的目录类型
     * @return 当前有效记录数
     */
    long countActiveByType(@Param("organizationId") long organizationId,
                           @Param("directoryType") HospitalDirectoryType directoryType);

    /**
     * 保存单一目录类型本轮同步的事实结果。
     *
     * <p>同一批次和类型只保留最新一次可确认结论；该操作与批次终态在同一事务提交。</p>
     *
     * @param batchId 同步运行主键
     * @param result 单一目录类型的取得、校验和更新事实
     */
    void saveDirectoryResult(@Param("batchId") long batchId,
                             @Param("result") HospitalDirectorySyncResultVO result);

    /**
     * 读取一个100-003批次按目录类型持久化的运行事实。
     *
     * @param batchId 同步批次主键
     * @return 按HIS目录类型编码升序的分项结果；历史批次可能为空
     */
    List<HospitalDirectorySyncResultVO> findResults(@Param("batchId") long batchId);

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
