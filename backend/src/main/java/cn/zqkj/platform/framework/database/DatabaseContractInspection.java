package cn.zqkj.platform.framework.database;

import java.util.List;
import java.util.Map;

/**
 * 数据库契约一次只读检查的结果。
 *
 * @param expectedColumnCount 迁移契约中的预期字段数量
 * @param violations 已按错误码和对象名稳定排序的差异
 * @param expectedColumns 以字段全名索引的Flyway预期结构
 * @param actualColumns 以字段全名索引的数据库实际结构
 */
public record DatabaseContractInspection(
        int expectedColumnCount,
        List<DatabaseContractViolation> violations,
        Map<String, DatabaseContractColumn> expectedColumns,
        Map<String, DatabaseContractColumn> actualColumns
) {

    /**
     * 固化结果集合，避免调用方在记录或展示后继续修改。
     *
     * @param expectedColumnCount 迁移契约中的预期字段数量
     * @param violations 已按错误码和对象名稳定排序的差异
     * @param expectedColumns 以字段全名索引的Flyway预期结构
     * @param actualColumns 以字段全名索引的数据库实际结构
     */
    public DatabaseContractInspection {
        violations = List.copyOf(violations);
        expectedColumns = Map.copyOf(expectedColumns);
        actualColumns = Map.copyOf(actualColumns);
    }

    /**
     * 创建不需要字段快照的检查结果，主要用于启动门禁单元测试。
     *
     * @param expectedColumnCount 预期字段数量
     * @param violations 差异集合
     */
    public DatabaseContractInspection(int expectedColumnCount,
                                      List<DatabaseContractViolation> violations) {
        this(expectedColumnCount, violations, Map.of(), Map.of());
    }

    /**
     * 判断检查是否通过。
     *
     * @return 没有发现契约差异时返回true
     */
    public boolean passed() {
        return violations.isEmpty();
    }

    /**
     * 统计某一建议修改方向的差异数量。
     *
     * @param direction 建议修改方向
     * @return 该方向的差异数量
     */
    public long count(DatabaseContractViolation.Direction direction) {
        return violations.stream().filter(item -> item.direction() == direction).count();
    }
}
