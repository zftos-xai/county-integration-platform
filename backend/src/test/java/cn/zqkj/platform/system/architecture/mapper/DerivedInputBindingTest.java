package cn.zqkj.platform.system.architecture.mapper;

import cn.zqkj.platform.exchange.domain.dto.ExchangeRecordQuery;
import cn.zqkj.platform.masterdata.domain.batch.dto.MasterDataBatchQuery;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCreation;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataSyncMode;
import cn.zqkj.platform.system.configuration.domain.dto.CreateExternalEndpointRequest;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateExternalEndpointRequest;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.organization.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.organization.domain.dto.UpdateOrganizationCommand;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证复用入口 record 后，MyBatis 真正能读取派生属性，而不只是生成参数占位符。 */
class DerivedInputBindingTest {

    /**
     * 批次、交换记录和机构输入的偏移时间通过实际 XML 属性路径绑定为 UTC。
     * @throws Exception 读取 Mapper 资源失败时抛出
     */
    @Test
    void bindsUtcDatesThroughActualMapperPaths() throws Exception {
        Configuration configuration = parse("mapper/masterdata/batch/MasterDataBatchMapper.xml",
                "mapper/exchange/ExchangeRecordMapper.xml", "mapper/system/organization/OrganizationMapper.xml");
        OffsetDateTime start = OffsetDateTime.parse("2026-09-21T08:00:00+08:00");
        OffsetDateTime end = start.plusDays(1);
        LocalDateTime utcStart = LocalDateTime.parse("2026-09-21T00:00:00");
        LocalDateTime utcEnd = utcStart.plusDays(1);
        assertBindings(configuration, "cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper.findPage",
                Map.of("query", new MasterDataBatchQuery(null, null, null, null, start, end, 1, 20),
                        "organizationCodes", List.of("ORG001")),
                Map.of("query.startedFromUtc", utcStart, "query.startedToUtc", utcEnd));
        assertBindings(configuration, "cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper.create",
                Map.of("creation", new MasterDataBatchCreation("8BCDCA4E11A44A9D888D7E70", "ORG001",
                        ParameterEnvironment.TEST, MasterDataCategory.MEDICAL_DIRECTORY, MasterDataSyncMode.TIME_RANGE,
                        utcStart, utcEnd, 9L, new byte[8], null)),
                Map.of("creation.rangeStart", utcStart, "creation.rangeEnd", utcEnd));
        assertBindings(configuration, "cn.zqkj.platform.exchange.mapper.ExchangeRecordMapper.findRecentByOrganization",
                new ExchangeRecordQuery("ORG001", start, end, null, null, null, null, 20),
                Map.of("receivedFromUtc", utcStart, "receivedToUtc", utcEnd));
        assertBindings(configuration, "cn.zqkj.platform.system.organization.mapper.OrganizationMapper.create",
                Map.of("command", new CreateOrganizationCommand("ORG001", "测试机构", "HOSPITAL", null, start, end)),
                Map.of("command.validFromUtc", utcStart, "command.validToUtc", utcEnd));
        assertBindings(configuration, "cn.zqkj.platform.system.organization.mapper.OrganizationMapper.update",
                Map.of("command", new UpdateOrganizationCommand("测试机构", "HOSPITAL", null, start, end, new byte[8])),
                Map.of("command.validFromUtc", utcStart, "command.validToUtc", utcEnd));
    }

    /**
     * 端点创建和更新绑定同样的标准化地址，保存 ASMX 操作参数但移除路径点段。
     * @throws Exception 读取 Mapper 资源失败时抛出
     */
    @Test
    void bindsNormalizedUrlsWithoutIntermediateCommand() throws Exception {
        Configuration configuration = parse("mapper/system/configuration/ConfigurationMapper.xml");
        String input = "http://his.invalid/old/../WebService.asmx?op=PHIS_Interface";
        Map<String, Object> expected = Map.of("command.normalizedBaseUrl",
                "http://his.invalid/WebService.asmx?op=PHIS_Interface");
        assertBindings(configuration, "cn.zqkj.platform.system.configuration.mapper.ConfigurationMapper.createExternalEndpoint",
                Map.of("command", new CreateExternalEndpointRequest(ParameterEnvironment.TEST, 10L,
                        input, 3000, 15000, null, false)), expected);
        assertBindings(configuration, "cn.zqkj.platform.system.configuration.mapper.ConfigurationMapper.updateExternalEndpoint",
                Map.of("command", new UpdateExternalEndpointRequest(ParameterEnvironment.TEST, 10L,
                        input, 3000, 15000, null, false, new byte[8])), expected);
    }

    private void assertBindings(Configuration configuration, String statement, Object input, Map<String, Object> expected) {
        var bound = configuration.getMappedStatement(statement).getBoundSql(input);
        var parameters = configuration.newMetaObject(input);
        for (var entry : expected.entrySet()) {
            assertTrue(bound.getParameterMappings().stream()
                    .anyMatch(mapping -> mapping.getProperty().equals(entry.getKey())), statement + ": " + entry.getKey());
            assertEquals(entry.getValue(), parameters.getValue(entry.getKey()), statement + ": " + entry.getKey());
        }
    }

    private Configuration parse(String... resources) throws Exception {
        Configuration configuration = new Configuration();
        for (String resource : resources) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }
}
