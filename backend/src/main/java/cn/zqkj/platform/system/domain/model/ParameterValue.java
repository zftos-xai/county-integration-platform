package cn.zqkj.platform.system.domain.model;

import java.time.LocalDateTime;

/**
 * 平台参数在指定环境和机构范围下的已保存值。
 *
 * <p>数据来源：主表 {@code dbo.sys_parameter_value}（平台参数值表），可选关联
 * {@code dbo.org_organization}（机构表）补充机构代码。</p>
 *
 * <p>业务说明：参数定义和校验规则由代码注册表提供；本投影只表示按环境、
 * 机构作用域落库的值及并发版本。</p>
 *
 * @param id 参数值主键
 * @param parameterKey 注册参数键
 * @param valueType 保存时的注册类型
 * @param environment 部署环境
 * @param organizationId 可选机构主键
 * @param organizationCode 可选机构代码
 * @param value 规范化参数值
 * @param enabled 是否启用
 * @param createdAt 创建UTC时间
 * @param updatedAt 最后修改UTC时间
 * @param version SQL Server并发版本
 */
public record ParameterValue(
        long id,
        String parameterKey,
        ParameterValueType valueType,
        ParameterEnvironment environment,
        Long organizationId,
        String organizationCode,
        String value,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        byte[] version
) {
}
