package cn.zqkj.platform.framework.database;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.Objects;
import java.util.regex.Pattern;

/** 根据可信契约快照为有限的低风险字段差异生成SQL Server 2012 DDL。 */
@Component
public class DatabaseContractRepairPlanner {

    private static final Set<String> LENGTH_TYPES = Set.of(
            "nvarchar", "nchar", "varchar", "char", "varbinary", "binary");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    /**
     * 为一项实时差异生成受控修复语句。
     *
     * @param violation 实时检查发现的差异
     * @param expected Flyway预期字段
     * @param actual 数据库实际字段
     * @return 可执行DDL或明确拒绝原因
     */
    public DatabaseContractRepairSql plan(DatabaseContractViolation violation,
                                          DatabaseContractColumn expected,
                                          DatabaseContractColumn actual) {
        if (expected == null || actual == null) {
            return rejected("字段缺失或快照不完整，只能通过正式Flyway迁移处理");
        }
        if (!expected.qualifiedName().equals(actual.qualifiedName())
                || !expected.typeName().equals(actual.typeName()) || expected.identity() || expected.computed()
                || actual.identity() || actual.computed()) {
            return rejected("字段类型、IDENTITY或计算属性变化不允许在线自动执行");
        }
        if ("DBCONTRACT-E103".equals(violation.code())) {
            if (!LENGTH_TYPES.contains(expected.typeName())
                    || expected.maxLength() == null || actual.maxLength() == null
                    || actual.maxLength() == -1
                    || (expected.maxLength() != -1 && expected.maxLength() <= actual.maxLength())) {
                return rejected("字段缩短或非长度类型需要离线迁移和数据影响评审");
            }
            if (expected.nullable() != actual.nullable()
                    || !Objects.equals(expected.precision(), actual.precision())
                    || !Objects.equals(expected.scale(), actual.scale())) {
                return rejected("扩容不得同时改变可空性或精度");
            }
            return executable(expected, "仅扩大字段长度；事务失败会自动回滚");
        }
        if ("DBCONTRACT-E106".equals(violation.code()) && expected.nullable() && !actual.nullable()
                && Objects.equals(expected.maxLength(), actual.maxLength())
                && Objects.equals(expected.precision(), actual.precision())
                && Objects.equals(expected.scale(), actual.scale())) {
            return executable(expected, "仅放宽为允许NULL；事务失败会自动回滚");
        }
        return rejected("该差异需要数据核查或复杂迁移，不允许在线自动执行");
    }

    /**
     * 为确认可在线处理的字段差异生成DDL及执行说明。
     *
     * @param expected 预期字段
     * @param reason 执行说明
     * @return 可执行DDL
     */
    private DatabaseContractRepairSql executable(DatabaseContractColumn expected, String reason) {
        String type = typeSql(expected);
        if (type == null) return rejected("当前类型不能安全生成在线DDL，请使用正式迁移");
        requireIdentifier(expected.schemaName());
        requireIdentifier(expected.tableName());
        requireIdentifier(expected.columnName());
        String sql = "ALTER TABLE " + quote(expected.schemaName()) + "." + quote(expected.tableName())
                + " ALTER COLUMN " + quote(expected.columnName()) + " " + type
                + (expected.nullable() ? " NULL" : " NOT NULL") + ";";
        return new DatabaseContractRepairSql(true, sql, reason);
    }

    /**
     * 把契约字段转换为SQL Server 2012兼容的类型声明。
     *
     * @param expected 预期字段
     * @return SQL Server字段类型声明
     */
    private String typeSql(DatabaseContractColumn expected) {
        String type = expected.typeName().toUpperCase(java.util.Locale.ROOT);
        if (LENGTH_TYPES.contains(expected.typeName())) {
            int value = expected.maxLength() == -1 ? -1
                    : expected.typeName().startsWith("n") ? expected.maxLength() / 2 : expected.maxLength();
            return type + "(" + (value == -1 ? "MAX" : value) + ")";
        }
        return switch (expected.typeName()) {
            case "decimal", "numeric" -> expected.precision() == null || expected.scale() == null
                    ? null : type + "(" + expected.precision() + "," + expected.scale() + ")";
            case "datetime2", "datetimeoffset", "time" -> expected.scale() == null
                    ? null : type + "(" + expected.scale() + ")";
            case "float" -> expected.precision() == null ? null : type + "(" + expected.precision() + ")";
            case "bigint", "int", "smallint", "tinyint", "bit", "date", "datetime", "smalldatetime",
                    "money", "smallmoney", "real", "uniqueidentifier" -> type;
            default -> null;
        };
    }

    /**
     * 拒绝不能安全引用的数据库标识符。
     *
     * @param identifier 标识符
     */
    private void requireIdentifier(String identifier) {
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalStateException("数据库契约包含不安全标识符");
        }
    }

    /**
     * 使用SQL Server方括号规则引用已校验的标识符。
     *
     * @param identifier 标识符
     * @return SQL Server转义标识符
     */
    private String quote(String identifier) {
        return "[" + identifier + "]";
    }

    /**
     * 创建明确不可在线执行的修复规划结果。
     *
     * @param reason 拒绝原因
     * @return 不可执行结果
     */
    private DatabaseContractRepairSql rejected(String reason) {
        return new DatabaseContractRepairSql(false, null, reason);
    }
}
