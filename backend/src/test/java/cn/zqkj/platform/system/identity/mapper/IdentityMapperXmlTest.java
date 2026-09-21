package cn.zqkj.platform.system.identity.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证身份Mapper XML及显式机构管理范围含义。
 */
class IdentityMapperXmlTest {

    /**
     * 验证停用机构仍保留在显式管理范围中，以允许获授权管理员重新启用。
     */
    @Test
    void keepsDisabledOrganizationsInExplicitManagementScope() {
        String resource = "mapper/system/identity/IdentityMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "IdentityMapper.xml must be present");
            Configuration configuration = new Configuration();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            BoundSql boundSql = configuration.getMappedStatement(
                    "cn.zqkj.platform.system.identity.mapper.IdentityMapper.findOrganizationCodes"
            ).getBoundSql(1L);
            String normalizedSql = boundSql.getSql().replaceAll("\\s+", " ");

            assertTrue(normalizedSql.contains("sys_user_organization_scope"));
            assertFalse(normalizedSql.contains("organizations.is_enabled"));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to close mapper resource", exception);
        }
    }
}
