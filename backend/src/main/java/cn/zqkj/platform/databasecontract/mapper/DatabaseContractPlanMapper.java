package cn.zqkj.platform.databasecontract.mapper;

import cn.zqkj.platform.databasecontract.domain.model.DatabaseContractPlanItemSnapshot;
import cn.zqkj.platform.databasecontract.domain.model.DatabaseContractPlanSnapshot;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 持久化数据库契约维护方案、审批、执行和复验事实。 */
@Mapper
public interface DatabaseContractPlanMapper {

    /**
     * 写入数据库契约维护方案并返回数据库生成主键。
     *
     * @param planNo 方案编号
     * @param issueCount 差异数
     * @param executableCount 可执行数
     * @param summary 摘要
     * @param createdBy 创建人
     * @return 新方案主键
     */
    long insertPlan(@Param("planNo") String planNo, @Param("issueCount") int issueCount,
                    @Param("executableCount") int executableCount, @Param("summary") String summary,
                    @Param("createdBy") String createdBy);

    /**
     * 写入维护方案生成时固化的一项数据库契约差异。
     *
     * @param planId 方案主键
     * @param item 差异明细
     */
    void insertItem(@Param("planId") long planId,
                    @Param("item") DatabaseContractPlanItemSnapshot item);

    /**
     * 按稳定倒序查询最近的数据库契约维护方案。
     *
     * @return 最近100条维护方案
     */
    List<DatabaseContractPlanSnapshot> findRecent();

    /**
     * 按主键读取数据库契约维护方案；不存在时由调用边界按约定处理。
     *
     * @param id 方案主键
     * @return 方案快照
     */
    DatabaseContractPlanSnapshot findById(long id);

    /**
     * 查询差异明细。
     *
     * @param planId 方案主键
     * @return 差异明细
     */
    List<DatabaseContractPlanItemSnapshot> findItems(long planId);

    /**
     * 审批尚未执行的数据库契约维护方案。
     *
     * @param id 主键
     * @param version 并发版本
     * @param approvedBy 审批人
     * @param note 审批说明
     * @return 影响行数
     */
    int approve(@Param("id") long id, @Param("version") byte[] version,
                @Param("approvedBy") String approvedBy, @Param("note") String note);

    /**
     * 使用行版本将已批准方案原子推进到执行中状态。
     *
     * @param id 主键
     * @param version 并发版本
     * @param executedBy 执行人
     * @return 影响行数
     */
    int markExecuting(@Param("id") long id, @Param("version") byte[] version,
                      @Param("executedBy") String executedBy);

    /**
     * 在DDL执行和复验均成功后将方案标记为完成。
     *
     * @param id 方案主键
     */
    void markCompleted(long id);

    /**
     * 记录DDL事务失败后的受控错误摘要。
     *
     * @param id 方案主键
     * @param version 发起执行时的并发版本；事务回滚后只允许更新同一版本
     * @param message 失败摘要
     * @return 影响行数；版本或状态变化时为零
     */
    int markFailed(@Param("id") long id, @Param("version") byte[] version,
                   @Param("message") String message);

    /**
     * 在执行开始前取消维护方案。
     *
     * @param id 主键
     * @param version 并发版本
     * @return 影响行数
     */
    int cancel(@Param("id") long id, @Param("version") byte[] version);

    /**
     * 将方案下全部可执行明细标记为已执行。
     *
     * @param planId 方案主键
     */
    void markItemsExecuted(long planId);

    /**
     * 记录单项差异在执行后复验中的结果。
     *
     * @param id 明细主键
     * @param status 复验状态
     */
    void markItemVerification(@Param("id") long id, @Param("status") String status);
}
