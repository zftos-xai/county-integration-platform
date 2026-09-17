package cn.zqkj.platform.system.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证系统域MyBatis构造映射与Java记录的原生及包装类型完全一致。
 */
class MapperConstructorCompatibilityTest {

    private static final List<String> MAPPER_RESOURCES = List.of(
            "mapper/system/IdentityMapper.xml",
            "mapper/system/OrganizationMapper.xml",
            "mapper/system/AccessMapper.xml",
            "mapper/system/ConfigurationMapper.xml",
            "mapper/system/ManagementAuditMapper.xml"
    );

    /**
     * 验证每个构造结果映射都能找到参数类型完全匹配的Java构造器。
     */
    @Test
    void matchesEveryMappedConstructorSignature() {
        Configuration configuration = new Configuration();
        for (String resource : MAPPER_RESOURCES) {
            parseMapper(configuration, resource);
        }
        Set<ResultMap> resultMaps = new LinkedHashSet<>(configuration.getResultMaps());
        for (ResultMap resultMap : resultMaps) {
            if (resultMap.getConstructorResultMappings().isEmpty()) {
                continue;
            }
            Class<?>[] parameterTypes = resultMap.getConstructorResultMappings().stream()
                    .map(mapping -> mapping.getJavaType())
                    .toArray(Class<?>[]::new);
            assertDoesNotThrow(
                    () -> resultMap.getType().getDeclaredConstructor(parameterTypes),
                    () -> "Constructor mapping does not match " + resultMap.getId()
            );
        }
    }

    /**
     * 将指定Mapper XML加载到共享MyBatis配置。
     *
     * @param configuration 共享MyBatis配置
     * @param resource 类路径Mapper资源
     */
    private void parseMapper(Configuration configuration, String resource) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource + " must be present");
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to close mapper resource " + resource, exception);
        }
    }
}
