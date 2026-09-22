package cn.zqkj.platform.framework.database;

import cn.zqkj.platform.common.utils.Func;

import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.springframework.stereotype.Component;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** 在应用启动时核对MyBatis构造映射与Java记录组件。 */
@Component
public class MapperModelContractValidator {

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
     * 检查全部已加载MyBatis构造映射。
     *
     * @param configuration MyBatis运行时配置
     * @return Mapper或Java模型侧差异
     */
    public List<DatabaseContractViolation> validate(Configuration configuration) {
        List<DatabaseContractViolation> violations = new ArrayList<>();
        for (ResultMap resultMap : new LinkedHashSet<>(configuration.getResultMaps())) {
            List<ResultMapping> mappings = resultMap.getConstructorResultMappings();
            if (mappings.isEmpty()) {
                continue;
            }
            if (!resultMap.getType().isRecord()) {
                violations.add(violation("DBCONTRACT-E201", resultMap.getId(),
                        "构造映射目标为Java record", resultMap.getType().getName(),
                        "将只读结果改为record，或取消构造映射并提供受控属性映射"));
                continue;
            }
            RecordComponent[] components = resultMap.getType().getRecordComponents();
            if (components.length != mappings.size()) {
                violations.add(violation("DBCONTRACT-E202", resultMap.getId(),
                        components.length + "个Java组件", mappings.size() + "个resultMap字段",
                        "同步修改Java record、resultMap和SELECT列清单"));
                continue;
            }
            for (int index = 0; index < components.length; index++) {
                compareComponent(resultMap, components[index], mappings.get(index), violations);
            }
        }
        return List.copyOf(violations);
    }

    /**
     * 比较一个Java记录组件与对应的MyBatis构造字段。
     *
     * @param resultMap 当前结果映射
     * @param component Java记录组件
     * @param mapping MyBatis构造字段
     * @param violations 累积差异
     */
    private void compareComponent(ResultMap resultMap, RecordComponent component, ResultMapping mapping,
                                  List<DatabaseContractViolation> violations) {
        String objectName = resultMap.getId() + "#" + component.getName();
        if (!component.getType().equals(mapping.getJavaType())) {
            violations.add(violation("DBCONTRACT-E203", objectName,
                    component.getType().getName(), mapping.getJavaType().getName(),
                    "修改resultMap javaType或Java record组件类型；不得依赖自动装箱碰巧成功"));
        }
        String expectedColumn = expectedColumn(resultMap.getType(), component.getName());
        if (!expectedColumn.equals(mapping.getColumn())) {
            violations.add(violation("DBCONTRACT-E204", objectName,
                    expectedColumn, mapping.getColumn(),
                    "修正resultMap column或SELECT别名，使其与Java组件业务含义一致"));
        }
    }

    /**
     * 计算记录组件应使用的查询结果列名。
     *
     * @param recordType Java记录类型
     * @param componentName 记录组件名
     * @return 预期结果列名
     */
    private String expectedColumn(Class<?> recordType, String componentName) {
        String overrideKey = recordType.getName() + "#" + componentName;
        if (COLUMN_NAME_OVERRIDES.containsKey(overrideKey)) {
            return COLUMN_NAME_OVERRIDES.get(overrideKey);
        }
        return COMPONENT_COLUMN_CONVENTIONS.getOrDefault(componentName, Func.toSnakeCase(componentName));
    }

    /**
     * 创建Mapper或Java模型侧差异。
     *
     * @param code 稳定错误码
     * @param objectName 映射对象
     * @param expected 预期值
     * @param actual 实际值
     * @param action 修复建议
     * @return Mapper或模型差异
     */
    private DatabaseContractViolation violation(String code, String objectName, String expected,
                                                String actual, String action) {
        return new DatabaseContractViolation(
                code,
                DatabaseContractViolation.Direction.MAPPER_OR_MODEL,
                objectName,
                expected,
                actual,
                action
        );
    }
}
