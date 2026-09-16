package cn.zqkj.platform.modules.exchange.infrastructure.persistence;

import cn.zqkj.platform.modules.exchange.application.ExchangeRuntimeRecord;
import cn.zqkj.platform.modules.exchange.application.ExchangeRuntimeRecordRepository;
import org.springframework.stereotype.Repository;

/**
 * 使用 MyBatis 保存交换模块最小运行记录。
 *
 * <p>该适配器不执行外部调用、结果推断、补发或完整报文保存。</p>
 */
@Repository
public class MyBatisExchangeRuntimeRecordRepository implements ExchangeRuntimeRecordRepository {

    private final ExchangeRecordMapper mapper;

    /**
     * 创建交换运行记录持久化适配器。
     *
     * @param mapper 交换记录 MyBatis Mapper
     */
    public MyBatisExchangeRuntimeRecordRepository(ExchangeRecordMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int save(ExchangeRuntimeRecord record) {
        return mapper.insertRuntimeRecord(record);
    }
}
