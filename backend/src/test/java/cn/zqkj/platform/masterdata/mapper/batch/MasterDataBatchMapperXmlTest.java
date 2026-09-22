package cn.zqkj.platform.masterdata.mapper.batch;

import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCreation;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataScopeType;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataSyncMode;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.sql.JDBCType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证基础数据批次Mapper XML语法和SQL Server可空字段绑定。
 */
class MasterDataBatchMapperXmlTest {

    /** 执行栅栏必须同时锁定状态与版本，未知结果不能直接重跑同一批次。 */
    @Test
    void fencesWritesAndRejectsUnknownBatchRerun() {
        Configuration configuration = parseMapper();
        String prefix = "cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper.";
        var parameters = Map.of("batchId", 25L, "expectedVersion", new byte[8], "actor", "admin");
        String lock = configuration.getMappedStatement(prefix + "lockExecution").getBoundSql(parameters).getSql();
        assertTrue(lock.contains("UPDLOCK, HOLDLOCK"));
        assertTrue(lock.contains("row_version = ?"));
        assertTrue(lock.contains("batch_status IN ('FETCHING', 'RESULT_UNKNOWN')"));
        String claim = configuration.getMappedStatement(prefix + "beginFetch").getBoundSql(parameters).getSql();
        assertTrue(claim.contains("batch_status = 'CREATED'"));
        assertFalse(claim.contains("RESULT_UNKNOWN"));
    }

    /** 批次使用已解析创建事实，类别决定交易，空时间具有明确 JDBC 类型。 */
    @Test
    void bindsNullableBatchFieldsWithExplicitJdbcTypes() {
        Configuration configuration = parseMapper();
        MappedStatement statement = configuration.getMappedStatement(
                "cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper.create"
        );
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("organizationId", null);
        parameters.put("creation", new MasterDataBatchCreation(
                "8BCDCA4E11A44A9D888D7E70", "ORG001",
                ParameterEnvironment.TEST,
                MasterDataCategory.HOSPITAL_DIRECTORY, MasterDataSyncMode.NOT_APPLICABLE,
                null, null, 9L, new byte[8], null));

        BoundSql boundSql = statement.getBoundSql(parameters);
        Map<String, JDBCType> jdbcTypes = new HashMap<>();
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            if (mapping.getJdbcType() != null) {
                jdbcTypes.put(mapping.getProperty(), JDBCType.valueOf(mapping.getJdbcType().name()));
            }
        }

        assertEquals(JDBCType.BIGINT, jdbcTypes.get("organizationId"));
        assertEquals(JDBCType.TIMESTAMP, jdbcTypes.get("creation.rangeStart"));
        assertEquals(JDBCType.TIMESTAMP, jdbcTypes.get("creation.rangeEnd"));
        assertEquals(JDBCType.NVARCHAR, jdbcTypes.get("countTradeCode"));
        assertEquals("100-003", boundSql.getAdditionalParameter("dataTradeCode"));
        assertEquals(null, boundSql.getAdditionalParameter("countTradeCode"));
        assertEquals(MasterDataScopeType.ORGANIZATION,
                boundSql.getAdditionalParameter("scopeType"));
    }

    /** 按 ID 读取和取消操作在空机构范围下只能命中平台级批次。 */
    @Test
    void scopesBatchReadsAndCancellationInSql() {
        Configuration configuration = parseMapper();
        String mapper = "cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper.";
        String visibleSql = configuration.getMappedStatement(mapper + "findVisibleById")
                .getBoundSql(Map.of("id", 25L, "organizationCodes", List.of())).getSql();
        String cancelSql = configuration.getMappedStatement(mapper + "cancel")
                .getBoundSql(Map.of("id", 25L, "expectedVersion", new byte[8],
                        "actor", "admin", "organizationCodes", List.of())).getSql();

        assertTrue(visibleSql.contains("batches.scope_type = 'PLATFORM'"));
        assertFalse(visibleSql.contains("organizations.organization_code IN"));
        assertTrue(cancelSql.contains("scope_type = 'PLATFORM'"));
        assertFalse(cancelSql.contains("organization_id IN"));

        String scopedRead = configuration.getMappedStatement(mapper + "findVisibleById")
                .getBoundSql(Map.of("id", 25L, "organizationCodes", List.of("ORG001"))).getSql();
        String scopedCancel = configuration.getMappedStatement(mapper + "cancel")
                .getBoundSql(Map.of("id", 25L, "expectedVersion", new byte[8],
                        "actor", "admin", "organizationCodes", List.of("ORG001"))).getSql();
        assertTrue(scopedRead.contains("organizations.organization_code IN"));
        assertTrue(scopedCancel.contains("organization_id IN"));
    }

    /**
     * 装载并解析基础数据同步Mapper XML。
     *
     * @return 已装载批次Mapper的MyBatis配置
     */
    private Configuration parseMapper() {
        String resource = "mapper/masterdata/batch/MasterDataBatchMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "MasterDataBatchMapper.xml must be present");
            Configuration configuration = new Configuration();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            return configuration;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to close mapper resource", exception);
        }
    }
}
