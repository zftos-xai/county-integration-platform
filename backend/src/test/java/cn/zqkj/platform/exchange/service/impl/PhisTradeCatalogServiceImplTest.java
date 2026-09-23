package cn.zqkj.platform.exchange.service.impl;

import cn.zqkj.platform.exchange.domain.vo.PhisTradeVO;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证管理端交易目录契约始终来自PhisTrade定义。 */
class PhisTradeCatalogServiceImplTest {

    /** 验证正常交易和未收录交易都输出完整元数据与准确文档状态。 */
    @Test
    void mapsAuthoritativeTradeDefinitions() {
        List<PhisTradeVO> trades = new PhisTradeCatalogServiceImpl().findAll();

        PhisTradeVO documented = trades.stream().filter(trade -> "100-003".equals(trade.code())).findFirst()
                .orElseThrow();
        PhisTradeVO undocumented = trades.stream().filter(trade -> "806".equals(trade.code())).findFirst()
                .orElseThrow();

        assertEquals("医院综合目录查询", documented.displayName());
        assertTrue(documented.documentedInPublicSpecification());
        assertFalse(undocumented.documentedInPublicSpecification());
        assertTrue(undocumented.description().contains("公版接口文档未收录"));
    }
}
