package cn.zqkj.platform.modules.exchange.infrastructure.persistence;

import cn.zqkj.platform.modules.exchange.application.ExchangeRecordQuery;
import cn.zqkj.platform.modules.exchange.application.ExchangeRecordQueryRepository;
import cn.zqkj.platform.modules.exchange.application.ExchangeRecordSummary;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 基于 MyBatis 的交换记录查询实现。
 *
 * <p>该适配器只执行查询，不拥有交换状态变化或重试规则。</p>
 */
@Repository
public class MyBatisExchangeRecordQueryRepository implements ExchangeRecordQueryRepository {

    private final ExchangeRecordMapper mapper;

    /**
     * 创建 MyBatis 查询适配器。
     *
     * @param mapper 交换记录 Mapper
     */
    public MyBatisExchangeRecordQueryRepository(ExchangeRecordMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ExchangeRecordSummary> findRecent(ExchangeRecordQuery query) {
        return mapper.findRecentByOrganization(query);
    }
}
