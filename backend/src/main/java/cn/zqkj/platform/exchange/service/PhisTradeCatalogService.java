package cn.zqkj.platform.exchange.service;

import cn.zqkj.platform.exchange.domain.vo.PhisTradeVO;
import java.util.List;

/** 提供供管理端使用的HIS交易目录查询。 */
public interface PhisTradeCatalogService {

    /**
     * 按协议目录声明顺序查询全部交易定义。
     *
     * @return 交易目录；无登记交易时为空列表
     */
    List<PhisTradeVO> findAll();
}
