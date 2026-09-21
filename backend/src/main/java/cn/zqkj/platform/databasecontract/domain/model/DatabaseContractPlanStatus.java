package cn.zqkj.platform.databasecontract.domain.model;

/** 数据库契约维护方案状态。 */
public enum DatabaseContractPlanStatus {
    /** 等待审批，可由创建人取消。 */ DRAFT,
    /** 已由非创建人批准，等待执行。 */ APPROVED,
    /** DDL事务正在执行。 */ EXECUTING,
    /** 可执行项完成且已经复验。 */ COMPLETED,
    /** 执行失败且DDL事务已经回滚。 */ FAILED,
    /** 执行前主动取消。 */ CANCELLED
}
