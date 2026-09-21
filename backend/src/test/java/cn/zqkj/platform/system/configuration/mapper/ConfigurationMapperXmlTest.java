package cn.zqkj.platform.system.configuration.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证平台配置Mapper XML能够被MyBatis解析。
 */
class ConfigurationMapperXmlTest {

    /** 验证配置Mapper命名空间、结果映射和SQL节点语法。 */
    @Test
    void parsesConfigurationMapperXml() {
        String resource = "mapper/system/configuration/ConfigurationMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "ConfigurationMapper.xml must be present");
            Configuration configuration = new Configuration();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to close mapper resource", exception);
        }
    }
}
