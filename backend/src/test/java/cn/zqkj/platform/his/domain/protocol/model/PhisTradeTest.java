package cn.zqkj.platform.his.domain.protocol.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证基层HIS交易目录能够稳定解释正式和补充交易码。
 */
class PhisTradeTest {

    /** 验证正式接口交易码返回中文名称和业务分类。 */
    @Test
    void resolvesDocumentedTradeMetadata() {
        PhisTrade trade = PhisTrade.findByCode("100-003").orElseThrow();

        assertEquals("医院综合目录查询", trade.displayName());
        assertEquals("基础数据", trade.category());
    }

    /** 验证外部平台补充短码只作为可识别的扩展交易登记。 */
    @Test
    void resolvesSupplementalHealthTradeMetadata() {
        PhisTrade trade = PhisTrade.findByCode("806").orElseThrow();

        assertEquals("可疑门诊患者列表", trade.displayName());
        assertEquals("医防融合扩展", trade.category());
    }

    /** 验证交易目录不存在重复编码，避免日志名称不确定。 */
    @Test
    void containsNoDuplicateCodes() {
        long distinctCodeCount = Arrays.stream(PhisTrade.values())
                .map(PhisTrade::code)
                .distinct()
                .count();

        assertEquals(PhisTrade.values().length, distinctCodeCount);
    }
}
