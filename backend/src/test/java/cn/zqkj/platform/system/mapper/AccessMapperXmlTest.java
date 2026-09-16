package cn.zqkj.platform.system.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证授权MyBatis映射可被当前MyBatis版本完整解析。
 */
class AccessMapperXmlTest {

    /**
     * 验证全部用户角色管理语句已注册且XML动态片段合法。
     *
     * @throws Exception 资源读取或映射解析失败时抛出
     */
    @Test
    void parsesAccessMapper() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/system/AccessMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        assertTrue(configuration.hasStatement(
                "cn.zqkj.platform.system.mapper.AccessMapper.replaceUserRoles"
        ));
        assertTrue(configuration.hasStatement(
                "cn.zqkj.platform.system.mapper.AccessMapper.countPermissions"
        ));
    }
}
