package cn.zqkj.platform.masterdata.mapper.medicaldirectory;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySyncResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 读写100-004医疗目录的当前有效数据和按类型运行事实。
 */
@Mapper
public interface MedicalDirectorySyncMapper {

    /**
     * 使用行版本将批次原子推进到取数状态。
     *
     * @param batchId 同步批次主键
     * @param expectedVersion 最近读取版本
     * @param actor 操作人
     * @return 成功进入取数状态时为1
     */
    int beginFetch(@Param("batchId") long batchId, @Param("expectedVersion") byte[] expectedVersion,
                   @Param("actor") String actor);

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
     */
    void updateDirectoryDirect(@Param("batchId") long batchId, @Param("organizationId") long organizationId,
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
     * 在某目录类型完整返回且校验通过时，将当前范围未返回的旧记录标记无效。
     *
     * @param batchId 已完整同步批次主键
     * @param organizationId 平台机构主键
     * @param directoryType 已完成的医疗目录类型
     * @return 本次标记无效数
     */
    int markMissingDirectoryInvalid(@Param("batchId") long batchId, @Param("organizationId") long organizationId,
                                    @Param("directoryType") MedicalDirectoryType directoryType);

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
                             @Param("result") MedicalDirectorySyncResult result);

    /**
     * 读取一个100-004/100-005批次按医疗目录类型持久化的运行事实。
     *
     * @param batchId 同步批次主键
     * @return 按HIS目录类型编码升序的分项结果
     */
    List<MedicalDirectorySyncResult> findResults(@Param("batchId") long batchId);

    /**
     * 写入一次已完成或部分异常医疗目录同步的全部汇总事实。
     *
     * @param batchId 同步批次主键
     * @param status 完成状态
     * @param declaredCount 所有可确认来源声明数之和
     * @param returnedCount 所有实际返回数之和
     * @param duplicateCount 重复数
     * @param invalidCount 无效数
     * @param conflictCount 冲突数
     * @param createdCount 新增数
     * @param updatedCount 更新数
     * @param unchangedCount 未变化数
     * @param sourceMissingCount 标记无效数
     * @param activeCount 当前有效数
     * @param failureCode 可选部分异常代码
     * @param failureSummary 可选部分异常摘要
     * @param actor 操作人
     * @return 更新行数
     */
    int finishCompleted(@Param("batchId") long batchId, @Param("status") String status,
                        @Param("declaredCount") Long declaredCount, @Param("returnedCount") long returnedCount,
                        @Param("duplicateCount") long duplicateCount, @Param("invalidCount") long invalidCount,
                        @Param("conflictCount") long conflictCount, @Param("createdCount") long createdCount,
                        @Param("updatedCount") long updatedCount, @Param("unchangedCount") long unchangedCount,
                        @Param("sourceMissingCount") long sourceMissingCount, @Param("activeCount") long activeCount,
                        @Param("failureCode") String failureCode, @Param("failureSummary") String failureSummary,
                        @Param("actor") String actor);
}
