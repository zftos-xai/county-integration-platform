package cn.zqkj.platform.system.configuration.domain.dto;

import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;

/**
 * 删除一个指定适用范围参数配置的服务命令。
 *
 * @param environment 部署环境
 * @param organizationId 可选机构主键
 * @param expectedVersion 当前并发版本
 */
public record DeleteParameterCommand(
        ParameterEnvironment environment,
        Long organizationId,
        byte[] expectedVersion
) {
}
