package cn.zqkj.platform.system.domain.vo;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 可供管理端构建明确表单的代码注册参数元数据。
 *
 * @param key 参数键
 * @param name 展示名称
 * @param valueType 值类型
 * @param environments 允许环境
 * @param organizationScoped 是否为机构级参数
 * @param sensitive 是否隐藏已保存值
 * @param maximumLength 文本最大长度
 * @param minimumNumber 数值下界
 * @param maximumNumber 数值上界
 * @param pattern 文本正则约束
 */
public record ParameterDefinitionVO(
        String key,
        String name,
        String valueType,
        Set<String> environments,
        boolean organizationScoped,
        boolean sensitive,
        Integer maximumLength,
        BigDecimal minimumNumber,
        BigDecimal maximumNumber,
        String pattern
) {
}
