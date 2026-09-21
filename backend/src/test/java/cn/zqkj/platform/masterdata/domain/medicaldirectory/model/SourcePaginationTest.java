package cn.zqkj.platform.masterdata.domain.medicaldirectory.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证来源声明数量到分页请求范围的确定性转换。 */
class SourcePaginationTest {

    /** 验证最后一页不会超出声明数量，也不遗漏末行。 */
    @Test
    void plansInclusivePagesWithoutGapOrOverflow() {
        assertEquals(List.of(new SourcePage(1, 100), new SourcePage(101, 200), new SourcePage(201, 205)),
                SourcePagination.plan(205, 100));
        assertEquals(List.of(), SourcePagination.plan(0, 100));
    }

    /** 验证非法声明数和页大小不会生成错误来源请求。 */
    @Test
    void rejectsInvalidPageInputs() {
        assertThrows(IllegalArgumentException.class, () -> SourcePagination.plan(-1, 100));
        assertThrows(IllegalArgumentException.class, () -> SourcePagination.plan(1, 0));
    }
}
