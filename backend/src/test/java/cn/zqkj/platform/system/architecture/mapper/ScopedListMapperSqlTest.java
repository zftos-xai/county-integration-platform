package cn.zqkj.platform.system.architecture.mapper;

import cn.zqkj.platform.masterdata.domain.batch.dto.MasterDataBatchQuery;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.dto.MedicalDirectoryQuery;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证管理列表的机构范围由SQL约束，空范围不会退化为全表查询。 */
class ScopedListMapperSqlTest {

    /** 按 ID 读取也要在 SQL 内限制机构，不能先加载范围外的对象。 */
    @Test
    void scopesIdLookupsAndLocksUserAffiliationForManagementWrites() {
        Configuration configuration = parse("mapper/system/identity/AccessMapper.xml",
                "mapper/system/organization/OrganizationMapper.xml",
                "mapper/system/configuration/ConfigurationMapper.xml");
        String user = sql(configuration, "cn.zqkj.platform.system.identity.mapper.AccessMapper.findUser",
                Map.of("userId", 2L, "organizationCodes", List.of()));
        String organization = sql(configuration,
                "cn.zqkj.platform.system.organization.mapper.OrganizationMapper.findVisibleById",
                Map.of("id", 10L, "organizationCodes", List.of()));
        String endpoint = sql(configuration,
                "cn.zqkj.platform.system.configuration.mapper.ConfigurationMapper.findExternalEndpoint",
                Map.of("endpointId", 9L, "organizationCodes", List.of()));
        assertTrue(user.contains("AND 1 = 0"));
        assertTrue(user.contains("WITH (UPDLOCK, HOLDLOCK)"));
        assertTrue(organization.contains("AND 1 = 0"));
        assertTrue(endpoint.contains("endpoints.organization_id IS NULL"));
        assertTrue(!endpoint.contains("organizations.organization_code IN"));
        for (String statement : List.of("markExternalEndpointVerified", "markExternalEndpointVerificationFailed")) {
            var bound = configuration.getMappedStatement(
                    "cn.zqkj.platform.system.configuration.mapper.ConfigurationMapper." + statement)
                    .getBoundSql(Map.of());
            assertTrue(bound.getSql().contains("AND row_version = ?"));
            assertTrue(bound.getParameterMappings().stream()
                    .anyMatch(parameter -> parameter.getProperty().equals("expectedVersion")));
        }
    }

    /** 用户及机构列表在空授权范围时必须生成拒绝全表读取的条件。 */
    @Test
    void deniesEmptyOrganizationScopeInIdentityAndOrganizationQueries() {
        Configuration configuration = parse(
                "mapper/system/identity/AccessMapper.xml",
                "mapper/system/organization/OrganizationMapper.xml");
        for (String statement : List.of("findUsers", "findUserRoles", "findUserOrganizations")) {
            String sql = sql(configuration, "cn.zqkj.platform.system.identity.mapper.AccessMapper." + statement,
                    Map.of("organizationCodes", List.of()));
            assertTrue(sql.contains("WHERE 1 = 0"), statement);
        }
        String organizations = sql(configuration,
                "cn.zqkj.platform.system.organization.mapper.OrganizationMapper.findVisible",
                Map.of("organizationCodes", List.of()));
        assertTrue(organizations.contains("WHERE 1 = 0"));
    }

    /** 配置列表在空授权范围时只可返回全局配置，不可读取机构级配置。 */
    @Test
    void limitsConfigurationQueriesToGlobalValuesWhenScopeIsEmpty() {
        Configuration configuration = parse("mapper/system/configuration/ConfigurationMapper.xml");
        String parameters = sql(configuration,
                "cn.zqkj.platform.system.configuration.mapper.ConfigurationMapper.findParameterValues",
                Map.of("organizationCodes", List.of()));
        String endpoints = sql(configuration,
                "cn.zqkj.platform.system.configuration.mapper.ConfigurationMapper.findExternalEndpoints",
                Map.of("organizationCodes", List.of(), "systemId", 1L));

        assertTrue(parameters.contains("WHERE values_table.organization_id IS NULL"));
        assertTrue(endpoints.contains("endpoints.organization_id IS NULL"));
        assertTrue(!parameters.contains("organizations.organization_code IN"));
        assertTrue(!endpoints.contains("organizations.organization_code IN"));
    }

    /** 目录空机构范围拒绝机构行；批次空范围仅保留平台级批次。 */
    @Test
    void keepsMasterDataListScopesWhenOrganizationSetIsEmpty() {
        Configuration configuration = parse(
                "mapper/masterdata/hospitaldirectory/HospitalDirectoryCatalogMapper.xml",
                "mapper/masterdata/medicaldirectory/MedicalDirectoryCatalogMapper.xml",
                "mapper/masterdata/batch/MasterDataBatchMapper.xml");
        String hospitalDirectory = sql(configuration,
                "cn.zqkj.platform.masterdata.mapper.hospitaldirectory.HospitalDirectoryCatalogMapper.countPage",
                Map.of("query", new HospitalDirectoryQuery(null, null, null, 1, 20),
                        "organizationCodes", List.of()));
        String medicalDirectory = sql(configuration,
                "cn.zqkj.platform.masterdata.mapper.medicaldirectory.MedicalDirectoryCatalogMapper.countPage",
                Map.of("query", new MedicalDirectoryQuery(null, null, null, 1, 20),
                        "organizationCodes", List.of()));
        String batches = sql(configuration,
                "cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper.countPage",
                Map.of("query", new MasterDataBatchQuery(null, null, null, null, null, null, 1, 20),
                        "organizationCodes", List.of()));

        assertTrue(hospitalDirectory.contains("WHERE 1 = 0"));
        assertTrue(medicalDirectory.contains("WHERE 1 = 0"));
        assertTrue(batches.contains("batches.scope_type = 'PLATFORM'"));
        assertTrue(!batches.contains("organizations.organization_code IN"));
    }

    /**
     * 读取动态语句生成的SQL文本。
     *
     * @param configuration 已解析的Mapper配置
     * @param statement 完整Mapper语句标识
     * @param parameters 查询参数
     * @return 去除首尾空白的SQL文本
     */
    private String sql(Configuration configuration, String statement, Map<String, Object> parameters) {
        BoundSql bound = configuration.getMappedStatement(statement).getBoundSql(parameters);
        return bound.getSql().trim();
    }

    /**
     * 装载本测试涉及的MyBatis XML映射文件。
     *
     * @param resources 类路径下的Mapper XML
     * @return 已解析配置
     */
    private Configuration parse(String... resources) {
        Configuration configuration = new Configuration();
        for (String resource : resources) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource + " must be present");
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            } catch (IOException exception) {
                throw new IllegalStateException("无法读取Mapper XML: " + resource, exception);
            }
        }
        return configuration;
    }
}
