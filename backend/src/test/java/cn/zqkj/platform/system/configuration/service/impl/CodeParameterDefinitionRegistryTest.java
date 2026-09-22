package cn.zqkj.platform.system.configuration.service.impl;

import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.configuration.domain.model.ParameterValueType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证代码注册参数默认配置保持最小、稳定且可供管理端构建明确表单。 */
class CodeParameterDefinitionRegistryTest {

    private final CodeParameterDefinitionRegistry registry = new CodeParameterDefinitionRegistry();

    /** 验证管理列表分页默认值的注册类型、环境与范围。 */
    @Test
    void registersManagementListPageSize() {
        var definition = registry.find("management.list.default-page-size").orElseThrow();

        assertEquals(ParameterValueType.INTEGER, definition.valueType());
        assertEquals(Set.of(ParameterEnvironment.DEVELOPMENT, ParameterEnvironment.TEST,
                ParameterEnvironment.PRODUCTION), definition.environments());
        assertEquals(BigDecimal.TEN, definition.minimumNumber());
        assertEquals(BigDecimal.valueOf(100), definition.maximumNumber());
        assertFalse(definition.organizationScoped());
        assertFalse(definition.sensitive());
        assertTrue(definition.name().contains("未接入运行"));
    }

    /** 验证审计查询默认条数与现有API最大限制一致。 */
    @Test
    void registersAuditDefaultQueryLimit() {
        var definition = registry.find("management.audit.default-query-limit").orElseThrow();

        assertEquals(ParameterValueType.INTEGER, definition.valueType());
        assertEquals(BigDecimal.TEN, definition.minimumNumber());
        assertEquals(BigDecimal.valueOf(200), definition.maximumNumber());
        assertTrue(registry.findAll().contains(definition));
        assertEquals(2, registry.findAll().size());
        assertTrue(definition.name().contains("未接入运行"));
    }
}
