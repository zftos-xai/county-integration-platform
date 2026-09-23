package cn.zqkj.platform.exchange.service.impl;

import cn.zqkj.platform.exchange.domain.vo.PhisTradeVO;
import cn.zqkj.platform.exchange.service.PhisTradeCatalogService;
import cn.zqkj.platform.his.domain.protocol.model.PhisTrade;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/** 将权威HIS交易定义转换为稳定的管理端目录输出。 */
@Service
public class PhisTradeCatalogServiceImpl implements PhisTradeCatalogService {

    /**
     * 返回交易码、中文名称、说明与公版文档收录状态。
     *
     * @return 按PhisTrade声明顺序排列的交易目录
     */
    @Override
    public List<PhisTradeVO> findAll() {
        return Arrays.stream(PhisTrade.values())
                .map(trade -> new PhisTradeVO(
                        trade.code(),
                        trade.displayName(),
                        trade.category(),
                        trade.description(),
                        trade.documentedInPublicSpecification()))
                .toList();
    }
}
