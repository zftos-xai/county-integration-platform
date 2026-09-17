package cn.zqkj.platform.system.domain.model;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 由后端代码注册的平台参数元数据和校验边界。
 *
 * @param key 稳定参数键
 * @param name 展示名称
 * @param valueType 值类型
 * @param environments 允许配置的环境
 * @param organizationScoped 是否必须绑定机构
 * @param sensitive 返回时是否隐藏值
 * @param maximumLength 文本最大长度；非文本或无限制时为空
 * @param minimumNumber 数值下界；无限制时为空
 * @param maximumNumber 数值上界；无限制时为空
 * @param pattern 文本正则约束；无限制时为空
 */
public record ParameterDefinition(
        String key,
        String name,
        ParameterValueType valueType,
        Set<ParameterEnvironment> environments,
        boolean organizationScoped,
        boolean sensitive,
        Integer maximumLength,
        BigDecimal minimumNumber,
        BigDecimal maximumNumber,
        String pattern
) {
}
