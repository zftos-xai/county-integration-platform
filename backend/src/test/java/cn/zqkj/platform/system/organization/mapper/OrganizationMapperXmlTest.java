package cn.zqkj.platform.system.organization.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.JDBCType;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证机构Mapper XML语法及SQL Server 2012可空字段的JDBC类型绑定。
 */
class OrganizationMapperXmlTest {

    /** 机构树写锁限定于事务并设置等待上限，避免连接池连接长期持锁。 */
    @Test
    void usesBoundedTransactionOwnedHierarchyLock() {
        String sql = parseMapper().getMappedStatement(
                "cn.zqkj.platform.system.organization.mapper.OrganizationMapper.lockHierarchy").getBoundSql(null).getSql();
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("sys.sp_getapplock"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("@LockOwner = 'Transaction'"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("@LockTimeout = 10000"));
    }

    /**
     * 验证创建机构时可空父机构和有效期不会被MyBatis按VARBINARY绑定。
     */
    @Test
    void bindsNullableOrganizationFieldsWithExplicitJdbcTypes() {
        Configuration configuration = parseMapper();
        MappedStatement statement = configuration.getMappedStatement(
                "cn.zqkj.platform.system.organization.mapper.OrganizationMapper.create"
        );
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("command", new cn.zqkj.platform.system.organization.domain.dto.CreateOrganizationCommand(
                "HOSPITAL.001", "县人民医院", "HOSPITAL", null, null, null
        ));
        parameters.put("actor", "test-admin");

        BoundSql boundSql = statement.getBoundSql(parameters);
        Map<String, JDBCType> jdbcTypes = new HashMap<>();
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            if (mapping.getJdbcType() != null) {
                jdbcTypes.put(mapping.getProperty(), JDBCType.valueOf(mapping.getJdbcType().name()));
            }
        }

        assertEquals(JDBCType.BIGINT, jdbcTypes.get("command.parentId"));
        assertEquals(JDBCType.TIMESTAMP, jdbcTypes.get("command.validFromUtc"));
        assertEquals(JDBCType.TIMESTAMP, jdbcTypes.get("command.validToUtc"));
    }

    /** 直接复用 HTTP 输入时，MyBatis 仍将偏移时间转换为 SQL Server 约定的 UTC。 */
    @Test
    void resolvesUtcDatesFromBoundOrganizationCommand() {
        var command = new cn.zqkj.platform.system.organization.domain.dto.CreateOrganizationCommand(
                "ORG001", "测试机构", "HOSPITAL", null,
                java.time.OffsetDateTime.parse("2026-09-21T08:00:00+08:00"),
                java.time.OffsetDateTime.parse("2026-09-22T08:00:00+08:00"));
        var parameters = parseMapper().newMetaObject(Map.of("command", command));
        assertEquals(java.time.LocalDateTime.parse("2026-09-21T00:00:00"),
                parameters.getValue("command.validFromUtc"));
        assertEquals(java.time.LocalDateTime.parse("2026-09-22T00:00:00"),
                parameters.getValue("command.validToUtc"));
    }

    /**
     * 验证机构结果映射使用与记录构造器一致的原生布尔值和字节数组。
     */
    @Test
    void mapsPrimitiveConstructorTypesWithoutBoxingMismatch() {
        Configuration configuration = parseMapper();
        ResultMap resultMap = configuration.getResultMap(
                "cn.zqkj.platform.system.organization.mapper.OrganizationMapper.organizationView"
        );
        Map<String, Class<?>> javaTypes = new HashMap<>();
        for (ResultMapping mapping : resultMap.getConstructorResultMappings()) {
            javaTypes.put(mapping.getColumn(), mapping.getJavaType());
        }

        assertEquals(boolean.class, javaTypes.get("is_enabled"));
        assertEquals(byte[].class, javaTypes.get("row_version"));
    }

    /**
     * 解析机构Mapper供绑定规则断言使用。
     *
     * @return 已装载机构Mapper的MyBatis配置
     */
    private Configuration parseMapper() {
        String resource = "mapper/system/organization/OrganizationMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "OrganizationMapper.xml must be present");
            Configuration configuration = new Configuration();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            return configuration;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to close mapper resource", exception);
        }
    }
}
