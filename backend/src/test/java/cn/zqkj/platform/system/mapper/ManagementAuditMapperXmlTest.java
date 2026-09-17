package cn.zqkj.platform.system.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 验证管理审计Mapper XML可被MyBatis解析。 */
class ManagementAuditMapperXmlTest {

    /** 验证只追加和授权查询SQL节点语法。 */
    @Test
    void parsesManagementAuditMapperXml() {
        String resource = "mapper/system/ManagementAuditMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "ManagementAuditMapper.xml must be present");
            Configuration configuration = new Configuration();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to close mapper resource", exception);
        }
    }
}
