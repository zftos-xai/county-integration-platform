package cn.zqkj.platform.databasecontract.service;

import cn.zqkj.platform.databasecontract.domain.dto.CreateDatabaseContractPlanRequest;
import cn.zqkj.platform.databasecontract.domain.model.DatabaseContractPlanSnapshot;
import cn.zqkj.platform.databasecontract.domain.vo.DatabaseContractInspectionVO;
import cn.zqkj.platform.databasecontract.domain.vo.DatabaseContractPlanVO;
import cn.zqkj.platform.system.domain.model.AccessActor;

import java.util.List;

/** 定义健康实例中的数据库契约扫描和受控维护闭环。 */
public interface DatabaseContractMaintenanceService {

    /**
     * 实时扫描数据库、迁移契约、Mapper和Java模型的一致性，不执行任何DDL。
     *
     * @return 当前数据库、Mapper和模型的实时只读检查结果
     */
    DatabaseContractInspectionVO inspect();

    /**
     * 查询最近的数据库契约维护方案。
     *
     * @return 最近维护方案
     */
    List<DatabaseContractPlanVO> findRecentPlans();

    /**
     * 按主键读取数据库契约维护方案及明细。
     *
     * @param id 方案主键
     * @return 最新方案
     */
    DatabaseContractPlanVO getPlan(long id);

    /**
     * 根据当前仍存在的契约差异创建维护方案。
     *
     * @param request 创建请求
     * @param actor 当前用户
     * @return 新方案
     */
    DatabaseContractPlanVO createPlan(CreateDatabaseContractPlanRequest request, AccessActor actor);

    /**
     * 审批尚未执行的数据库契约维护方案。
     *
     * @param id 方案主键
     * @param version 并发版本
     * @param note 审批说明
     * @param actor 当前用户
     * @return 已批准方案
     */
    DatabaseContractPlanVO approve(long id, byte[] version, String note, AccessActor actor);

    /**
     * 执行已批准方案并在同一受控流程中完成复验。
     *
     * @param id 方案主键
     * @param version 并发版本
     * @param actor 当前用户
     * @return 执行和复验后的方案
     */
    DatabaseContractPlanVO execute(long id, byte[] version, AccessActor actor);

    /**
     * 在执行开始前取消维护方案。
     *
     * @param id 方案主键
     * @param version 并发版本
     * @param actor 当前用户
     * @return 已取消方案
     */
    DatabaseContractPlanVO cancel(long id, byte[] version, AccessActor actor);
}
