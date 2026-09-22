package cn.zqkj.platform.system.configuration.service.impl;

import cn.zqkj.platform.system.configuration.domain.model.ParameterDefinition;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.configuration.service.ParameterDefinitionRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

import static cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment.DEVELOPMENT;
import static cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment.PRODUCTION;
import static cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment.TEST;
import static cn.zqkj.platform.system.configuration.domain.model.ParameterValueType.INTEGER;

/**
 * 保存代码评审批准的平台注册参数清单。
 *
 * <p>管理默认项目前只保存配置，不参与医疗目录查询范围计算。</p>
 */
@Component
public class CodeParameterDefinitionRegistry implements ParameterDefinitionRegistry {

    private static final Set<ParameterEnvironment> ALL_ENVIRONMENTS = Set.of(DEVELOPMENT, TEST, PRODUCTION);

    private static final List<ParameterDefinition> DEFINITIONS = List.of(
            new ParameterDefinition(
                    "management.list.default-page-size",
                    "管理列表默认每页条数（未接入运行）",
                    INTEGER,
                    ALL_ENVIRONMENTS,
                    false,
                    false,
                    null,
                    BigDecimal.TEN,
                    BigDecimal.valueOf(100),
                    null
            ),
            new ParameterDefinition(
                    "management.audit.default-query-limit",
                    "管理审计默认查询条数（未接入运行）",
                    INTEGER,
                    ALL_ENVIRONMENTS,
                    false,
                    false,
                    null,
                    BigDecimal.TEN,
                    BigDecimal.valueOf(200),
                    null
            )
    );

    /** 按登记顺序返回工程批准的参数定义，数据库不能扩展该清单。 */
    @Override
    public List<ParameterDefinition> findAll() {
        return DEFINITIONS;
    }

    /** 按完整参数键读取已登记定义，未登记时返回空结果。 */
    @Override
    public Optional<ParameterDefinition> find(String key) {
        return DEFINITIONS.stream().filter(definition -> definition.key().equals(key)).findFirst();
    }
}
