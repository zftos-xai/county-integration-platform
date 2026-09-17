package cn.zqkj.platform.system.service.impl;

import cn.zqkj.platform.system.domain.model.ParameterDefinition;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.service.ParameterDefinitionRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static cn.zqkj.platform.system.domain.model.ParameterEnvironment.DEVELOPMENT;
import static cn.zqkj.platform.system.domain.model.ParameterEnvironment.PRODUCTION;
import static cn.zqkj.platform.system.domain.model.ParameterEnvironment.TEST;
import static cn.zqkj.platform.system.domain.model.ParameterValueType.INTEGER;

/**
 * 保存代码评审批准的平台注册参数清单。
 *
 * <p>清单只包含项目已确认的平台管理默认项；新增参数仍必须通过代码评审加入，不能由API动态创建。</p>
 */
@Component
public class CodeParameterDefinitionRegistry implements ParameterDefinitionRegistry {

    private static final Set<ParameterEnvironment> ALL_ENVIRONMENTS = Set.of(DEVELOPMENT, TEST, PRODUCTION);

    private static final List<ParameterDefinition> DEFINITIONS = List.of(
            new ParameterDefinition(
                    "management.list.default-page-size",
                    "管理列表默认每页条数",
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
                    "管理审计默认查询条数",
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

    /** {@inheritDoc} */
    @Override
    public List<ParameterDefinition> findAll() {
        return DEFINITIONS;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ParameterDefinition> find(String key) {
        return DEFINITIONS.stream().filter(definition -> definition.key().equals(key)).findFirst();
    }
}
