package cn.zqkj.platform.exchange.service;

import cn.zqkj.platform.exchange.domain.dto.ExchangeRecordQuery;
import cn.zqkj.platform.exchange.domain.vo.ExchangeRecordVO;

import java.util.List;

/**
 * 定义交换运行记录的只读查询服务。
 */
public interface ExchangeRecordQueryService {

    /**
     * 按机构范围和有界数量查询最近交换记录。
     *
     * @param query 有界查询条件
     * @return 最近交换记录
     */
    List<ExchangeRecordVO> findRecent(ExchangeRecordQuery query);
}
