package cn.zqkj.platform.framework.database;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 比较Flyway预期字段与真实SQL Server字段并生成可操作差异。 */
@Component
public class DatabaseSchemaContractComparator {

    /**
     * 比较预期和实际数据库字段。
     *
     * @param expectedColumns Flyway迁移定义的预期字段
     * @param actualColumns SQL Server系统目录返回的实际字段
     * @return 全部结构差异
     */
    public List<DatabaseContractViolation> compare(
            Map<String, DatabaseContractColumn> expectedColumns,
            Map<String, DatabaseContractColumn> actualColumns) {
        List<DatabaseContractViolation> violations = new ArrayList<>();
        for (DatabaseContractColumn expected : expectedColumns.values()) {
            DatabaseContractColumn actual = actualColumns.get(expected.qualifiedName());
            if (actual == null) {
                violations.add(databaseViolation(
                        "DBCONTRACT-E101", expected, expected.describe(), "字段不存在",
                        "执行或修复包含该字段的Flyway迁移；不要通过删除Mapper字段规避"));
                continue;
            }
            compareColumn(expected, actual, violations);
        }
        for (DatabaseContractColumn actual : actualColumns.values()) {
            if (!expectedColumns.containsKey(actual.qualifiedName())) {
                violations.add(new DatabaseContractViolation(
                        "DBCONTRACT-E109",
                        DatabaseContractViolation.Direction.MANUAL_REVIEW,
                        actual.qualifiedName(),
                        "当前Flyway迁移中不存在该字段",
                        actual.describe(),
                        "确认是否存在未经Flyway执行的人工改表；需要保留时新增正式迁移和契约依据"
                ));
            }
        }
        return List.copyOf(violations);
    }

    /**
     * 比较一个字段的类型、长度、精度、空值、自增及计算属性。
     *
     * @param expected 预期字段
     * @param actual 实际字段
     * @param violations 累积差异
     */
    private void compareColumn(DatabaseContractColumn expected, DatabaseContractColumn actual,
                               List<DatabaseContractViolation> violations) {
        if (!expected.typeName().equals(actual.typeName())) {
            violations.add(databaseViolation("DBCONTRACT-E102", expected,
                    expected.typeName(), actual.typeName(),
                    "数据库类型与Flyway契约不同；通过正式迁移修正数据库，禁止仅修改javaType掩盖"));
        }
        compareValue("DBCONTRACT-E103", expected, "最大长度", expected.maxLength(), actual.maxLength(),
                "字段长度与Flyway契约不同；核对需求后通过正式迁移调整数据库长度", violations);
        compareValue("DBCONTRACT-E104", expected, "精度", expected.precision(), actual.precision(),
                "数值精度与Flyway契约不同；通过正式迁移修正，避免静默舍入", violations);
        compareValue("DBCONTRACT-E105", expected, "小数位", expected.scale(), actual.scale(),
                "数值或时间精度与Flyway契约不同；通过正式迁移修正", violations);
        if (expected.nullable() != actual.nullable()) {
            violations.add(databaseViolation("DBCONTRACT-E106", expected,
                    expected.nullable() ? "允许NULL" : "NOT NULL",
                    actual.nullable() ? "允许NULL" : "NOT NULL",
                    "数据库空值规则与Flyway契约不同；先核对存量数据，再通过正式迁移修正"));
        }
        if (expected.identity() != actual.identity()) {
            violations.add(databaseViolation("DBCONTRACT-E107", expected,
                    "IDENTITY=" + expected.identity(), "IDENTITY=" + actual.identity(),
                    "数据库自增属性与Flyway契约不同；需要DBA评审迁移和数据回退方案"));
        }
        if (expected.computed() != actual.computed()) {
            violations.add(databaseViolation("DBCONTRACT-E108", expected,
                    "COMPUTED=" + expected.computed(), "COMPUTED=" + actual.computed(),
                    "数据库计算字段属性与Flyway契约不同；通过正式迁移修正"));
        }
    }

    /**
     * 比较一个仅在契约声明时才强制检查的可空元数据值。
     *
     * @param code 稳定错误码
     * @param expected 预期字段
     * @param label 元数据名称
     * @param expectedValue 预期值
     * @param actualValue 实际值
     * @param action 修复建议
     * @param violations 累积差异
     */
    private void compareValue(String code, DatabaseContractColumn expected, String label,
                              Integer expectedValue, Integer actualValue, String action,
                              List<DatabaseContractViolation> violations) {
        if (expectedValue != null && !expectedValue.equals(actualValue)) {
            violations.add(databaseViolation(code, expected,
                    label + '=' + displayLength(expected.typeName(), expectedValue),
                    label + '=' + displayLength(expected.typeName(), actualValue), action));
        }
    }

    /**
     * 将SQL Server字节长度转换成业务更易理解的字符长度。
     *
     * @param typeName SQL类型
     * @param value SQL Server元数据值
     * @return 用于报告的规范值
     */
    private String displayLength(String typeName, Integer value) {
        if (value == null) {
            return "未知";
        }
        if (value == -1) {
            return "max";
        }
        return typeName.startsWith("n") ? value / 2 + "字符/" + value + "字节" : value + "字节";
    }

    /**
     * 创建数据库侧结构差异。
     *
     * @param code 稳定错误码
     * @param column 目标字段
     * @param expected 预期值
     * @param actual 实际值
     * @param action 修复建议
     * @return 数据库侧差异
     */
    private DatabaseContractViolation databaseViolation(String code, DatabaseContractColumn column,
                                                        String expected, String actual, String action) {
        return new DatabaseContractViolation(
                code,
                DatabaseContractViolation.Direction.DATABASE,
                column.qualifiedName(),
                expected,
                actual,
                action
        );
    }
}
