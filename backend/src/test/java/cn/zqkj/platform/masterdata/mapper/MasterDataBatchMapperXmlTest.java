package cn.zqkj.platform.masterdata.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.JDBCType;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证基础数据批次Mapper XML语法和SQL Server可空字段绑定。
 */
class MasterDataBatchMapperXmlTest {

    /** 验证创建批次时可空机构、目录类型和查询范围具有明确JDBC类型。 */
    @Test
    void bindsNullableBatchFieldsWithExplicitJdbcTypes() {
        Configuration configuration = parseMapper();
        MappedStatement statement = configuration.getMappedStatement(
                "cn.zqkj.platform.masterdata.mapper.MasterDataBatchMapper.create"
        );
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("organizationId", null);
        parameters.put("sourceType", null);
        parameters.put("rangeStart", null);
        parameters.put("rangeEnd", null);
        parameters.put("diagnosisCategory", null);
        parameters.put("diagnosisVersion", null);
        parameters.put("countTradeCode", null);

        BoundSql boundSql = statement.getBoundSql(parameters);
        Map<String, JDBCType> jdbcTypes = new HashMap<>();
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            if (mapping.getJdbcType() != null) {
                jdbcTypes.put(mapping.getProperty(), JDBCType.valueOf(mapping.getJdbcType().name()));
            }
        }

        assertEquals(JDBCType.BIGINT, jdbcTypes.get("organizationId"));
        assertEquals(JDBCType.TIMESTAMP, jdbcTypes.get("rangeStart"));
        assertEquals(JDBCType.NVARCHAR, jdbcTypes.get("diagnosisVersion"));
    }

    /**
     * 装载并解析基础数据同步Mapper XML。
     *
     * @return 已装载批次Mapper的MyBatis配置
     */
    private Configuration parseMapper() {
        String resource = "mapper/masterdata/MasterDataBatchMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "MasterDataBatchMapper.xml must be present");
            Configuration configuration = new Configuration();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            return configuration;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to close mapper resource", exception);
        }
    }
}
