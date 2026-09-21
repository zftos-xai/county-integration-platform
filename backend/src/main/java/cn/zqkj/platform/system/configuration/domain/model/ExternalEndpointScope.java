package cn.zqkj.platform.system.configuration.domain.model;

/**
 * 已启用且可运行的外部系统端点范围投影，不包含地址或认证信息。
 *
 * <p>数据来源：主表 {@code dbo.sys_external_endpoint}（外部系统服务地址表），关联
 * {@code dbo.sys_external_system}、{@code dbo.org_organization}和
 * {@code dbo.sys_external_endpoint_credential}确认系统、机构及凭证可用性。</p>
 *
 * <p>业务说明：供业务域枚举当前用户有权发起同步的机构与运行环境。</p>
 *
 * @param organizationId 机构主键
 * @param organizationCode 机构代码
 * @param organizationName 机构名称
 * @param environment 运行环境
 */
public record ExternalEndpointScope(
        long organizationId,
        String organizationCode,
        String organizationName,
        ParameterEnvironment environment
) {
}
