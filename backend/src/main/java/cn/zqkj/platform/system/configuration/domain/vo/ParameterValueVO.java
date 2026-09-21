package cn.zqkj.platform.system.configuration.domain.vo;

import java.time.LocalDateTime;

/**
 * 平台按适用范围保存的参数值的安全输出。
 *
 * @param id 参数值主键
 * @param parameterKey 参数键
 * @param valueType 值类型
 * @param environment 部署环境
 * @param organizationId 可选机构主键
 * @param organizationCode 可选机构代码
 * @param value 普通值或敏感值掩码
 * @param configured 是否已有保存值
 * @param enabled 是否启用
 * @param updatedAt 最后修改UTC时间
 * @param version Base64编码并发版本
 */
public record ParameterValueVO(
        long id,
        String parameterKey,
        String valueType,
        String environment,
        Long organizationId,
        String organizationCode,
        String value,
        boolean configured,
        boolean enabled,
        LocalDateTime updatedAt,
        String version
) {
}
