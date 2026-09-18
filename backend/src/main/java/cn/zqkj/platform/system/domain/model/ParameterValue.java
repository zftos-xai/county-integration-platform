package cn.zqkj.platform.system.domain.model;

import java.time.LocalDateTime;

/**
 * 已保存的平台按适用范围保存的参数值。
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
