package cn.zqkj.platform.framework.database;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从项目Flyway建表脚本提取启动期物理字段契约。
 *
 * <p>迁移脚本是本项目数据库结构的权威来源。本解析器只接受项目当前约定的显式
 * {@code CREATE TABLE}字段写法；出现无法识别的结构变更时直接失败，避免静默漏检。</p>
 */
@Component
public class MigrationSchemaContractParser {

    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "(?ims)^CREATE\\s+TABLE\\s+(?:(?:\\[?([a-zA-Z_][\\w]*)]?)\\.)?"
                    + "\\[?([a-zA-Z_][\\w]*)]?\\s*\\((.*?)^\\);"
    );
    private static final Pattern COLUMN_PATTERN = Pattern.compile(
            "^\\s*\\[?([a-zA-Z_][\\w]*)]?\\s+([a-zA-Z][a-zA-Z0-9_]*)"
                    + "(?:\\((max|\\d+)(?:\\s*,\\s*(\\d+))?\\))?(?:\\s+(.*))?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ALTER_TABLE_ADD_PATTERN = Pattern.compile(
            "(?ims)^ALTER\\s+TABLE\\s+(?:(?:\\[?([a-zA-Z_][\\w]*)]?)\\.)?"
                    + "\\[?([a-zA-Z_][\\w]*)]?\\s+ADD\\s*(.*?);"
    );
    private static final Pattern ALTER_TABLE_DROP_CONSTRAINT_PATTERN = Pattern.compile(
            "(?im)^ALTER\\s+TABLE\\s+(?:(?:\\[?[a-zA-Z_][\\w]*]?)\\.)?"
                    + "\\[?[a-zA-Z_][\\w]*]?\\s+DROP\\s+CONSTRAINT\\s+\\[?[a-zA-Z_][\\w]*]?\\s*;"
    );
    private static final Pattern ALTER_TABLE_CHECKED_ADD_CONSTRAINT_PATTERN = Pattern.compile(
            "(?ims)^ALTER\\s+TABLE\\s+(?:(?:\\[?[a-zA-Z_][\\w]*]?)\\.)?"
                    + "\\[?[a-zA-Z_][\\w]*]?\\s+WITH\\s+CHECK\\s+ADD\\s+CONSTRAINT\\s+.*?;"
    );
    private static final Pattern ALTER_TABLE_PATTERN = Pattern.compile("(?im)^ALTER\\s+TABLE\\s+");

    private final ResourcePatternResolver resourceResolver;

    /**
     * 创建Flyway物理字段契约解析器。
     *
     * @param resourceResolver Spring类路径资源解析器
     */
    public MigrationSchemaContractParser(ResourcePatternResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    /**
     * 按迁移版本顺序读取全部迁移脚本并构建字段契约。
     *
     * @return 以字段全名索引的预期结构
     */
    public Map<String, DatabaseContractColumn> parse() {
        try {
            Resource[] resources = resourceResolver.getResources("classpath*:db/migration/V*.sql");
            java.util.Arrays.sort(resources, java.util.Comparator.comparing(Resource::getFilename));
            Map<String, DatabaseContractColumn> columns = new LinkedHashMap<>();
            for (Resource resource : resources) {
                String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                rejectUnsupportedSchemaChanges(resource, sql);
                parseCreateTables(resource, sql, columns);
                parseAlterTableAdds(resource, sql, columns);
            }
            if (columns.isEmpty()) {
                throw new IllegalStateException("未从Flyway迁移脚本解析出任何数据库字段契约");
            }
            return Map.copyOf(columns);
        } catch (IOException exception) {
            throw new IllegalStateException("读取Flyway迁移脚本失败", exception);
        }
    }

    /**
     * 仅接受可解析的新增字段、约束新增和约束删除；拒绝其他ALTER TABLE结构修改，
     * 防止字段契约静默漏检。
     *
     * @param resource 当前迁移资源
     * @param sql 迁移脚本文本
     */
    private void rejectUnsupportedSchemaChanges(Resource resource, String sql) {
        String supportedChangesRemoved = ALTER_TABLE_ADD_PATTERN.matcher(sql).replaceAll("");
        supportedChangesRemoved = ALTER_TABLE_CHECKED_ADD_CONSTRAINT_PATTERN
                .matcher(supportedChangesRemoved)
                .replaceAll("");
        supportedChangesRemoved = ALTER_TABLE_DROP_CONSTRAINT_PATTERN
                .matcher(supportedChangesRemoved)
                .replaceAll("");
        if (ALTER_TABLE_PATTERN.matcher(supportedChangesRemoved).find()) {
            throw new IllegalStateException(resource.getFilename()
                    + " 包含ALTER TABLE结构修改；必须先扩展数据库契约解析器再提交迁移");
        }
    }

    /**
     * 解析SQL Server的 {@code ALTER TABLE ... ADD} 新增字段，保证补丁或尚未共享的主版本
     * 新增字段同样进入启动期数据库契约。
     *
     * @param resource 当前迁移资源
     * @param sql 迁移脚本内容
     * @param columns 累积字段契约
     */
    private void parseAlterTableAdds(Resource resource, String sql, Map<String, DatabaseContractColumn> columns) {
        Matcher alterMatcher = ALTER_TABLE_ADD_PATTERN.matcher(sql);
        while (alterMatcher.find()) {
            String schemaName = normalizeIdentifier(alterMatcher.group(1) == null ? "dbo" : alterMatcher.group(1));
            String tableName = normalizeIdentifier(alterMatcher.group(2));
            String definitions = alterMatcher.group(3).trim();
            if (definitions.toUpperCase(Locale.ROOT).startsWith("CONSTRAINT")) {
                continue;
            }
            String[] lines = definitions.split("\\R");
            for (String line : lines) {
                parseColumn(resource, schemaName, tableName, line, columns);
            }
        }
    }

    /**
     * 解析一个迁移脚本中的全部CREATE TABLE字段。
     *
     * @param resource 当前迁移资源
     * @param sql 迁移脚本文本
     * @param columns 累积字段契约
     */
    private void parseCreateTables(Resource resource, String sql,
                                   Map<String, DatabaseContractColumn> columns) {
        Matcher tableMatcher = CREATE_TABLE_PATTERN.matcher(sql);
        while (tableMatcher.find()) {
            String schemaName = normalizeIdentifier(tableMatcher.group(1) == null
                    ? "dbo" : tableMatcher.group(1));
            String tableName = normalizeIdentifier(tableMatcher.group(2));
            String[] lines = tableMatcher.group(3).split("\\R");
            for (String line : lines) {
                parseColumn(resource, schemaName, tableName, line, columns);
            }
        }
    }

    /**
     * 解析CREATE TABLE中的一行字段定义并忽略约束行。
     *
     * @param resource 当前迁移资源
     * @param schemaName 数据库架构名称
     * @param tableName 表名
     * @param line 单行DDL
     * @param columns 累积字段契约
     */
    private void parseColumn(Resource resource, String schemaName, String tableName, String line,
                             Map<String, DatabaseContractColumn> columns) {
        String definition = line.replaceFirst("--.*$", "").trim().replaceFirst(",\\s*$", "");
        String uppercaseDefinition = definition.toUpperCase(Locale.ROOT);
        if (definition.isEmpty() || uppercaseDefinition.startsWith("CONSTRAINT")
                || uppercaseDefinition.startsWith("CHECK")) {
            return;
        }
        Matcher columnMatcher = COLUMN_PATTERN.matcher(definition);
        if (!columnMatcher.matches()) {
            throw new IllegalStateException(resource.getFilename() + " 无法解析字段定义: " + definition);
        }
        String columnName = normalizeIdentifier(columnMatcher.group(1));
        String typeName = normalizeType(columnMatcher.group(2));
        String firstArgument = columnMatcher.group(3);
        String secondArgument = columnMatcher.group(4);
        String remainder = columnMatcher.group(5) == null ? "" : columnMatcher.group(5);
        DatabaseContractColumn column = new DatabaseContractColumn(
                schemaName,
                tableName,
                columnName,
                typeName,
                maxLength(typeName, firstArgument),
                precision(typeName, firstArgument),
                scale(typeName, firstArgument, secondArgument),
                nullable(typeName, remainder),
                Pattern.compile("\\bIDENTITY\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(remainder).find(),
                Pattern.compile("\\bAS\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(remainder).find()
        );
        DatabaseContractColumn previous = columns.putIfAbsent(column.qualifiedName(), column);
        if (previous != null) {
            throw new IllegalStateException("Flyway迁移重复定义字段 " + column.qualifiedName());
        }
    }

    /**
     * 计算SQL Server系统目录使用的最大字节数。
     *
     * @param typeName SQL类型
     * @param argument 类型长度参数
     * @return 最大字节数，不适用时为空
     */
    private Integer maxLength(String typeName, String argument) {
        if (!java.util.Set.of("nvarchar", "nchar", "varchar", "char", "varbinary", "binary")
                .contains(typeName)) {
            return null;
        }
        if ("max".equalsIgnoreCase(argument)) {
            return -1;
        }
        int length = Integer.parseInt(argument);
        return typeName.startsWith("n") ? length * 2 : length;
    }

    /**
     * 提取decimal或numeric字段精度。
     *
     * @param typeName SQL类型
     * @param argument 首个类型参数
     * @return 数值精度，不适用时为空
     */
    private Integer precision(String typeName, String argument) {
        return java.util.Set.of("decimal", "numeric").contains(typeName)
                ? Integer.valueOf(argument) : null;
    }

    /**
     * 提取decimal、numeric或时间字段的小数位。
     *
     * @param typeName SQL类型
     * @param firstArgument 首个类型参数
     * @param secondArgument 第二个类型参数
     * @return 小数位，不适用时为空
     */
    private Integer scale(String typeName, String firstArgument, String secondArgument) {
        if (java.util.Set.of("decimal", "numeric").contains(typeName)) {
            return Integer.valueOf(secondArgument);
        }
        if (java.util.Set.of("datetime2", "datetimeoffset", "time").contains(typeName)) {
            return Integer.valueOf(firstArgument);
        }
        return null;
    }

    /**
     * 根据DDL空值约束计算字段是否允许为空。
     *
     * @param typeName SQL类型
     * @param remainder 类型之后的字段定义
     * @return 是否允许空值
     */
    private boolean nullable(String typeName, String remainder) {
        if ("rowversion".equals(typeName)) {
            return false;
        }
        return !Pattern.compile("\\bNOT\\s+NULL\\b", Pattern.CASE_INSENSITIVE)
                .matcher(remainder).find();
    }

    /**
     * 统一SQL Server类型同义词。
     *
     * @param value DDL类型名称
     * @return 小写规范类型名称
     */
    static String normalizeType(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return "timestamp".equals(normalized) ? "rowversion" : normalized;
    }

    /**
     * 统一数据库对象标识符大小写。
     *
     * @param value 数据库对象标识符
     * @return 小写标识符
     */
    private String normalizeIdentifier(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
