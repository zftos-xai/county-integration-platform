package cn.zqkj.platform.system.domain.dto;

import cn.zqkj.platform.system.domain.model.ParameterEnvironment;

/**
 * 新建或并发修改平台注册参数值的服务命令。
 *
 * @param environment 部署环境
 * @param organizationId 可选机构主键
 * @param value 参数值
 * @param enabled 是否启用
 * @param expectedVersion 已存在值的并发版本；首次创建时为空
 */
public record UpsertParameterCommand(
        ParameterEnvironment environment,
        Long organizationId,
        String value,
        boolean enabled,
        byte[] expectedVersion
) {
}
