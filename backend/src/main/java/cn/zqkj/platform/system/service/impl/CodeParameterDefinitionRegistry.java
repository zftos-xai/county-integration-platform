package cn.zqkj.platform.system.service.impl;

import cn.zqkj.platform.system.domain.model.ParameterDefinition;
import cn.zqkj.platform.system.service.ParameterDefinitionRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 保存代码评审批准的平台注册参数清单。
 *
 * <p>当前需求尚未批准具体参数键，因此清单保持为空；新增参数必须通过代码评审加入，不能由API动态创建。</p>
 */
@Component
public class CodeParameterDefinitionRegistry implements ParameterDefinitionRegistry {

    private static final List<ParameterDefinition> DEFINITIONS = List.of();

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
