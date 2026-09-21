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
        assertEquals(JDBCType.TIMESTAMP, jdbcTypes.get("command.validFrom"));
        assertEquals(JDBCType.TIMESTAMP, jdbcTypes.get("command.validTo"));
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
