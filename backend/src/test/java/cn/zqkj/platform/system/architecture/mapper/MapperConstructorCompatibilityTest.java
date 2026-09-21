package cn.zqkj.platform.system.architecture.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证全部MyBatis构造映射与Java记录组件的顺序、名称和类型完全一致。
 */
class MapperConstructorCompatibilityTest {

    private static final Map<String, String> COLUMN_NAME_OVERRIDES = Map.ofEntries(
            Map.entry("cn.zqkj.platform.system.identity.domain.model.UserAccount#enabled", "account_enabled"),
            Map.entry("cn.zqkj.platform.masterdata.domain.batch.model.PlatformOrganizationSnapshot#code",
                    "organization_code"),
            Map.entry("cn.zqkj.platform.masterdata.domain.batch.model.PlatformOrganizationSnapshot#name",
                    "organization_name"),
            Map.entry("cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot#environment",
                    "environment_code"),
            Map.entry("cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot#category",
                    "data_category"),
            Map.entry("cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot#rangeStart",
                    "query_started_at"),
            Map.entry("cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot#rangeEnd",
                    "query_ended_at"),
            Map.entry("cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot#status",
                    "batch_status"),
            Map.entry("cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot#version",
                    "row_version")
    );
    private static final Map<String, String> COMPONENT_COLUMN_CONVENTIONS = Map.of(
            "enabled", "is_enabled",
            "environment", "environment_code",
            "systemManaged", "is_system_managed",
            "value", "parameter_value",
            "version", "row_version"
    );

    /**
     * 验证自动发现的每个构造结果映射都与Java记录组件逐项一致。
     */
    @Test
    void matchesEveryMappedConstructorSignature() {
        Configuration configuration = new Configuration();
        for (String resource : findMapperResources()) {
            parseMapper(configuration, resource);
        }
        Set<ResultMap> resultMaps = new LinkedHashSet<>(configuration.getResultMaps());
        List<String> mismatches = new ArrayList<>();
        for (ResultMap resultMap : resultMaps) {
            if (resultMap.getConstructorResultMappings().isEmpty()) {
                continue;
            }
            assertTrue(resultMap.getType().isRecord(),
                    () -> resultMap.getId() + " constructor mapping must target a Java record");
            Class<?>[] parameterTypes = resultMap.getConstructorResultMappings().stream()
                    .map(mapping -> mapping.getJavaType())
                    .toArray(Class<?>[]::new);
            assertDoesNotThrow(
                    () -> resultMap.getType().getDeclaredConstructor(parameterTypes),
                    () -> "Constructor mapping does not match " + resultMap.getId()
            );
            java.lang.reflect.RecordComponent[] components = resultMap.getType().getRecordComponents();
            assertEquals(components.length, resultMap.getConstructorResultMappings().size(),
                    () -> resultMap.getId() + " component count does not match result mappings");
            for (int index = 0; index < components.length; index++) {
                java.lang.reflect.RecordComponent component = components[index];
                org.apache.ibatis.mapping.ResultMapping mapping =
                        resultMap.getConstructorResultMappings().get(index);
                if (!component.getType().equals(mapping.getJavaType())) {
                    mismatches.add(resultMap.getId() + " component " + component.getName()
                            + " expects Java type " + component.getType().getName()
                            + " but maps " + mapping.getJavaType().getName());
                }
                String expectedColumn = expectedColumn(resultMap.getType(), component.getName());
                if (!expectedColumn.equals(mapping.getColumn())) {
                    mismatches.add(resultMap.getId() + " component " + component.getName()
                            + " expects column " + expectedColumn + " but maps " + mapping.getColumn());
                }
            }
        }
        assertTrue(mismatches.isEmpty(), () -> String.join(System.lineSeparator(), mismatches));
    }

    /**
     * 自动发现生产类路径中所有MyBatis Mapper XML，避免新增业务域逃离一致性检查。
     *
     * @return 按资源路径稳定排序的全部Mapper XML
     */
    private List<String> findMapperResources() {
        try {
            java.net.URL mapperUrl = getClass().getClassLoader().getResource("mapper");
            assertNotNull(mapperUrl, "mapper resource directory must be present");
            Path mapperRoot = Path.of(mapperUrl.toURI());
            try (Stream<Path> paths = Files.walk(mapperRoot)) {
                return paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith("Mapper.xml"))
                        .map(path -> "mapper/" + mapperRoot.relativize(path).toString().replace('\\', '/'))
                        .sorted(Comparator.naturalOrder())
                        .toList();
            }
        } catch (java.io.IOException | URISyntaxException exception) {
            throw new IllegalStateException("Failed to discover mapper resources", exception);
        }
    }

    /**
     * 按默认驼峰转下划线规则或显式业务别名计算记录组件对应的结果列。
     *
     * @param recordType 结果记录类型
     * @param componentName Java记录组件名
     * @return Mapper结果映射必须使用的数据库结果列名
     */
    private String expectedColumn(Class<?> recordType, String componentName) {
        String overrideKey = recordType.getName() + "#" + componentName;
        if (COLUMN_NAME_OVERRIDES.containsKey(overrideKey)) {
            return COLUMN_NAME_OVERRIDES.get(overrideKey);
        }
        return COMPONENT_COLUMN_CONVENTIONS.getOrDefault(componentName, toSnakeCase(componentName));
    }

    /**
     * 将Java驼峰名称转换为数据库结果列使用的下划线名称。
     *
     * @param value Java记录组件名
     * @return 小写下划线列名
     */
    private String toSnakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
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
