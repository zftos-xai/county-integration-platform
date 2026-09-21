package cn.zqkj.platform.masterdata.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据来源声明数量生成不重叠、无遗漏的分页请求范围。
 *
 * <p>100-004/100-005的分页以一开始且包含结束行为约定；声明数为零时不产生分页请求。</p>
 */
public final class SourcePagination {

    /** 防止创建无状态分页规划器实例。 */
    private SourcePagination() {
    }

    /**
     * 按来源声明数量生成无重叠、无遗漏的分页范围。
     *
     * @param declaredCount 来源100-005声明行数
     * @param pageSize 单次最多取得行数
     * @return 覆盖全部声明行数的不可变分页范围
     */
    public static List<SourcePage> plan(long declaredCount, int pageSize) {
        if (declaredCount < 0 || pageSize < 1) {
            throw new IllegalArgumentException("来源分页数量或页大小无效");
        }
        List<SourcePage> pages = new ArrayList<>();
        for (long start = 1; start <= declaredCount; start += pageSize) {
            pages.add(new SourcePage(start, Math.min(declaredCount, start + pageSize - 1)));
        }
        return List.copyOf(pages);
    }
}
