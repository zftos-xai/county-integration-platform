package cn.zqkj.platform.modules.exchange.infrastructure.persistence;

import cn.zqkj.platform.modules.exchange.application.ExchangeRecordQuery;
import cn.zqkj.platform.modules.exchange.application.ExchangeRecordSummary;
import cn.zqkj.platform.modules.exchange.application.ExchangeRuntimeRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 使用 MyBatis 查询交换记录表。
 *
 * <p>每个查询都必须显式携带机构范围和有界数量。</p>
 */
@Mapper
public interface ExchangeRecordMapper {

    /**
     * 按机构查询最近的交换记录。
     *
     * @param query 包含强制机构范围、可选筛选条件和 SQL Server {@code TOP} 限制值的查询条件
     * @return 按接收时间和主键倒序排列的摘要；无数据时返回空列表
     */
    List<ExchangeRecordSummary> findRecentByOrganization(ExchangeRecordQuery query);

    /**
     * 插入一次已确认最终结果的交换运行记录。
     *
     * <p>完整业务正文、凭证和技术过程状态不进入该表；重复请求编号由数据库唯一约束拒绝。</p>
     *
     * @param record 已校验的最小运行记录
     * @return 实际插入的记录数量，应为 1
     */
    int insertRuntimeRecord(ExchangeRuntimeRecord record);
}
