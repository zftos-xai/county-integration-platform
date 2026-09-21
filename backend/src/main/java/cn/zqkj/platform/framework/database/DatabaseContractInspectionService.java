package cn.zqkj.platform.framework.database;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 执行数据库、Flyway字段契约、MyBatis映射与Java模型的统一只读检查。
 *
 * <p>启动门禁和后续正常维护功能共享本服务，确保两条线不会形成不同的判断标准。</p>
 */
@Service
public class DatabaseContractInspectionService {

    private static final String COLUMN_METADATA_SQL = """
            SELECT schemas.name AS schema_name,
                   tables.name AS table_name,
                   columns.name AS column_name,
                   types.name AS type_name,
                   columns.max_length,
                   columns.precision,
                   columns.scale,
                   columns.is_nullable,
                   columns.is_identity,
                   columns.is_computed
            FROM sys.tables AS tables
            INNER JOIN sys.schemas AS schemas ON schemas.schema_id = tables.schema_id
            INNER JOIN sys.columns AS columns ON columns.object_id = tables.object_id
            INNER JOIN sys.types AS types ON types.user_type_id = columns.user_type_id
            ORDER BY schemas.name, tables.name, columns.column_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final SqlSessionFactory sqlSessionFactory;
    private final MigrationSchemaContractParser contractParser;
    private final DatabaseSchemaContractComparator schemaComparator;
    private final MapperModelContractValidator mapperValidator;

    /**
     * 创建统一数据库契约检查服务。
     *
     * @param jdbcTemplate 平台数据源访问入口
     * @param sqlSessionFactory 已加载全部Mapper的MyBatis会话工厂
     * @param contractParser Flyway字段契约解析器
     * @param schemaComparator 数据库字段差异比较器
     * @param mapperValidator Mapper和Java模型一致性检查器
     */
    public DatabaseContractInspectionService(
            JdbcTemplate jdbcTemplate,
            SqlSessionFactory sqlSessionFactory,
            MigrationSchemaContractParser contractParser,
            DatabaseSchemaContractComparator schemaComparator,
            MapperModelContractValidator mapperValidator) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSessionFactory = sqlSessionFactory;
        this.contractParser = contractParser;
        this.schemaComparator = schemaComparator;
        this.mapperValidator = mapperValidator;
    }

    /**
     * 执行一次无DDL写入的完整契约检查。
     *
     * @return 稳定排序的检查结果
     */
    public DatabaseContractInspection inspect() {
        Map<String, DatabaseContractColumn> expectedColumns = contractParser.parse();
        Set<String> expectedTables = expectedColumns.values().stream()
                .map(column -> column.schemaName() + "." + column.tableName())
                .collect(Collectors.toUnmodifiableSet());
        Map<String, DatabaseContractColumn> actualColumns = loadActualColumns(expectedTables);

        List<DatabaseContractViolation> violations = new ArrayList<>();
        violations.addAll(validateServer());
        violations.addAll(schemaComparator.compare(expectedColumns, actualColumns));
        violations.addAll(mapperValidator.validate(sqlSessionFactory.getConfiguration()));
        violations.sort(Comparator.comparing(DatabaseContractViolation::code)
                .thenComparing(DatabaseContractViolation::objectName));
        return new DatabaseContractInspection(
                expectedColumns.size(), violations, expectedColumns, actualColumns);
    }

    /**
     * 检查SQL Server主版本和当前数据库兼容级别。
     *
     * @return 环境级数据库契约差异
     */
    private List<DatabaseContractViolation> validateServer() {
        Map<String, Object> server = jdbcTemplate.queryForMap("""
                SELECT CAST(SERVERPROPERTY('ProductVersion') AS NVARCHAR(128)) AS product_version,
                       compatibility_level
                FROM sys.databases
                WHERE name = DB_NAME()
                """);
        String productVersion = String.valueOf(server.get("product_version"));
        int majorVersion = Integer.parseInt(productVersion.substring(0, productVersion.indexOf('.')));
        int compatibilityLevel = ((Number) server.get("compatibility_level")).intValue();
        List<DatabaseContractViolation> violations = new ArrayList<>();
        if (majorVersion != 11) {
            violations.add(new DatabaseContractViolation(
                    "DBCONTRACT-E001", DatabaseContractViolation.Direction.DATABASE,
                    "SQL Server实例版本", "SQL Server 2012，主版本11", productVersion,
                    "切换到医院批准的SQL Server 2012 SP4实例；不得用其他版本结果代替兼容验证"));
        }
        if (compatibilityLevel != 110) {
            violations.add(new DatabaseContractViolation(
                    "DBCONTRACT-E002", DatabaseContractViolation.Direction.DATABASE,
                    "数据库兼容级别", "110", String.valueOf(compatibilityLevel),
                    "由DBA评估后将当前平台数据库兼容级别调整为110"));
        }
        return violations;
    }

    /**
     * 从SQL Server系统目录读取契约表的真实字段元数据。
     *
     * @param expectedTables Flyway契约包含的架构和表
     * @return 以字段全名索引的真实数据库结构
     */
    private Map<String, DatabaseContractColumn> loadActualColumns(Set<String> expectedTables) {
        Map<String, DatabaseContractColumn> columns = new LinkedHashMap<>();
        jdbcTemplate.query(COLUMN_METADATA_SQL, resultSet -> {
            String schemaName = resultSet.getString("schema_name").toLowerCase(Locale.ROOT);
            String tableName = resultSet.getString("table_name").toLowerCase(Locale.ROOT);
            if (!expectedTables.contains(schemaName + "." + tableName)) {
                return;
            }
            String typeName = MigrationSchemaContractParser.normalizeType(resultSet.getString("type_name"));
            DatabaseContractColumn column = new DatabaseContractColumn(
                    schemaName, tableName,
                    resultSet.getString("column_name").toLowerCase(Locale.ROOT), typeName,
                    lengthType(typeName) ? resultSet.getInt("max_length") : null,
                    numericType(typeName) ? resultSet.getInt("precision") : null,
                    scaleType(typeName) ? resultSet.getInt("scale") : null,
                    resultSet.getBoolean("is_nullable"), resultSet.getBoolean("is_identity"),
                    resultSet.getBoolean("is_computed"));
            columns.put(column.qualifiedName(), column);
        });
        return Map.copyOf(columns);
    }

    /** 判断SQL类型是否需要比较最大字节长度。 */
    private boolean lengthType(String typeName) {
        return Set.of("nvarchar", "nchar", "varchar", "char", "varbinary", "binary")
                .contains(typeName);
    }

    /** 判断SQL类型是否需要比较数值精度。 */
    private boolean numericType(String typeName) {
        return Set.of("decimal", "numeric").contains(typeName);
    }

    /** 判断SQL类型是否需要比较数值或时间小数位。 */
    private boolean scaleType(String typeName) {
        return numericType(typeName)
                || Set.of("datetime2", "datetimeoffset", "time").contains(typeName);
    }
}
