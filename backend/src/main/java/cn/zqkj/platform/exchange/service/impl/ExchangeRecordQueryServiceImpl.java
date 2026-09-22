package cn.zqkj.platform.exchange.service.impl;

import cn.zqkj.platform.exchange.domain.dto.ExchangeRecordQuery;
import cn.zqkj.platform.exchange.domain.vo.ExchangeRecordVO;
import cn.zqkj.platform.exchange.mapper.ExchangeRecordMapper;
import cn.zqkj.platform.exchange.service.ExchangeRecordQueryService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 编排交换记录的只读查询用例。
 *
 * <p>服务不会触发补发、状态变化或目标系统调用。</p>
 */
@Service
public class ExchangeRecordQueryServiceImpl implements ExchangeRecordQueryService {

    private final ExchangeRecordMapper mapper;

    /**
     * 创建交换记录查询服务。
     *
     * @param mapper 交换记录查询边界
     */
    public ExchangeRecordQueryServiceImpl(ExchangeRecordMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询指定机构最近的交换记录，并将返回数量限制为最多 100 条。
     *
     * <p>调用方必须在进入服务前完成机构权限校验。本方法使用只读事务，
     * 不触发任何再次写入。</p>
     *
     * @param query 查询条件，其中机构代码已由 API 层完成权限校验
     * @return 按接收时间倒序排列的交换记录摘要；无数据时返回空列表
     */
    @Transactional(readOnly = true)
    @Override
    public List<ExchangeRecordVO> findRecent(ExchangeRecordQuery query) {
        return mapper.findRecentByOrganization(query);
    }
}
