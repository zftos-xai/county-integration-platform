package cn.zqkj.platform.modules.exchange.application;

import java.util.List;

/**
 * 交换记录只读查询的持久化边界。
 *
 * <p>应用层依赖该接口，具体 MyBatis 实现位于基础设施层。</p>
 */
public interface ExchangeRecordQueryRepository {

    /**
     * 查询指定机构最近的交换记录。
     *
     * @param query 已包含强制机构范围和有界返回数量的查询条件
     * @return 按接收时间倒序排列的摘要；无数据时返回空列表
     */
    List<ExchangeRecordSummary> findRecent(ExchangeRecordQuery query);
}
