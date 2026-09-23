package cn.zqkj.platform.masterdata.mapper.icd10;

import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCounts;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10DiagnosisCategory;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10HisInvocation;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10SourceRecord;
import cn.zqkj.platform.masterdata.domain.icd10.dto.Icd10HisInvocationQuery;
import cn.zqkj.platform.masterdata.domain.icd10.vo.Icd10SyncResultVO;
import cn.zqkj.platform.masterdata.domain.icd10.vo.Icd10HisInvocationVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 读写平台公共ICD10当前目录和按类别同步事实。 */
@Mapper
public interface Icd10SyncMapper {

    /**
     * 保存一次已经确认终态的公共ICD10 HIS调用事实。
     *
     * @param invocation 不含完整报文和凭证的调用追踪记录
     * @return 实际插入行数，应为一
     */
    int insertHisInvocation(@Param("invocation") Icd10HisInvocation invocation);

    /**
     * 统计一个公共ICD10批次已保存的HIS调用事实数量。
     *
     * @param batchId 已通过批次可见性校验的主键
     * @return 调用事实总数；历史批次未接入追踪时为零
     */
    long countHisInvocations(@Param("batchId") long batchId);

    /**
     * 按类别与调用顺序读取一个公共ICD10批次的HIS调用事实页。
     *
     * @param batchId 已通过批次可见性校验的主键
     * @param query 服务端已校验的有界分页条件
     * @return 稳定排序的调用事实；没有记录时返回空列表
     */
    List<Icd10HisInvocationVO> findHisInvocations(@Param("batchId") long batchId,
                                                   @Param("query") Icd10HisInvocationQuery query);

    /**
     * 分类当前公共目录记录与本次已校验来源记录的差异。
     *
     * @param record 当前来源记录
     * @return 0新增、1更新、2未变化
     */
    int classifyDiagnosis(@Param("record") Icd10SourceRecord record);

    /**
     * 更新已有的公共ICD10目录记录。
     *
     * @param batchId 同步批次主键
     * @param record 已校验来源记录
     * @return 实际更新行数
     */
    int updateDiagnosisDirect(@Param("batchId") long batchId, @Param("record") Icd10SourceRecord record);

    /**
     * 插入首次出现的公共ICD10目录记录。
     *
     * @param batchId 同步批次主键
     * @param record 已校验来源记录
     * @return 实际插入行数；并发命中同键时为0
     */
    int insertDiagnosisDirect(@Param("batchId") long batchId, @Param("record") Icd10SourceRecord record);

    /**
     * 将一个尚无当前目录的诊断类别一次写入一小批来源记录。
     *
     * <p>调用方仅在持有批次执行栅栏、且该类别当前目录为空时使用本方法。每批大小受
     * SQL Server 2012 单条参数上限约束，仍以来源稳定身份避免并发重插。</p>
     *
     * @param batchId 当前同步批次主键
     * @param records 同一诊断类别内、已完成校验且来源稳定身份互异的记录
     * @return 实际插入行数
     */
    int insertDiagnosesDirect(@Param("batchId") long batchId, @Param("records") List<Icd10SourceRecord> records);

    /** @return 当前全部公共ICD10目录数量。 */
    long countActive();

    /**
     * 统计一个诊断类别的当前公共目录数量。
     *
     * @param category 诊断类别
     * @return 当前目录数量
     */
    long countActiveByCategory(@Param("category") Icd10DiagnosisCategory category);

    /**
     * 保存一个诊断类别的最终运行事实。
     *
     * @param batchId 批次主键
     * @param result 分项结果
     */
    void saveResult(@Param("batchId") long batchId, @Param("result") Icd10SyncResultVO result);

    /**
     * 查询批次已保存的分项结果。
     *
     * @param batchId 批次主键
     * @return 按类别编码升序的结果
     */
    List<Icd10SyncResultVO> findResults(@Param("batchId") long batchId);

    /**
     * 结束批次并持久化汇总事实。
     *
     * @param batchId 批次主键
     * @param status 结束状态
     * @param counts 汇总数量
     * @param failureCode 受控失败代码
     * @param failureSummary 受控失败摘要
     * @param actor 操作人
     * @return 状态仍可结束时为1
     */
    int finishCompleted(@Param("batchId") long batchId, @Param("status") String status,
                        @Param("counts") MasterDataBatchCounts counts,
                        @Param("failureCode") String failureCode,
                        @Param("failureSummary") String failureSummary, @Param("actor") String actor);
}
