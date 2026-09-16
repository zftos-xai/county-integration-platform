package cn.zqkj.platform.modules.exchange.application;

import cn.zqkj.platform.foundation.web.error.InvalidRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 编排交换记录的只读查询用例。
 *
 * <p>服务不会触发补发、状态变化或目标系统调用。</p>
 */
@Service
public class ExchangeRecordQueryService {

    private final ExchangeRecordQueryRepository repository;

    /**
     * 创建交换记录查询服务。
     *
     * @param repository 交换记录查询边界
     */
    public ExchangeRecordQueryService(ExchangeRecordQueryRepository repository) {
        this.repository = repository;
    }

    /**
     * 查询指定机构最近的交换记录，并将返回数量限制为最多 100 条。
     *
     * <p>调用方必须在进入服务前完成机构权限校验。本方法使用只读事务，
     * 不触发任何再次写入。</p>
     *
     * @param query 查询条件，其中机构代码已由 API 层完成权限校验
     * @return 按接收时间倒序排列的交换记录摘要；无数据时返回空列表
     * @throws InvalidRequestException 时间范围倒置或返回数量小于 1 时抛出
     */
    @Transactional(readOnly = true)
    public List<ExchangeRecordSummary> findRecent(ExchangeRecordQuery query) {
        if (query.limit() < 1) {
            throw new InvalidRequestException("limit must be at least one");
        }
        if (query.receivedFrom() != null
                && query.receivedTo() != null
                && query.receivedFrom().isAfter(query.receivedTo())) {
            throw new InvalidRequestException("receivedFrom must not be after receivedTo");
        }
        ExchangeRecordQuery boundedQuery = new ExchangeRecordQuery(
                query.organizationCode(),
                query.receivedFrom(),
                query.receivedTo(),
                query.interfaceCode(),
                query.sourceRecordId(),
                query.requestId(),
                query.result(),
                Math.min(query.limit(), 100)
        );
        return repository.findRecent(boundedQuery);
    }
}
