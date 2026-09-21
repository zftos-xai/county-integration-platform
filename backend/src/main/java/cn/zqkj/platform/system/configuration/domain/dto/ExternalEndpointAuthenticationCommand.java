package cn.zqkj.platform.system.configuration.domain.dto;

/**
 * 服务层使用的机构HIS接口认证信息。
 *
 * <p>该对象不得写入日志、审计摘要或API响应。</p>
 *
 * @param vendorCode 厂商编号
 * @param username 可选接口用户名
 * @param password 可选接口密码
 * @param authorizationCode HIS接口验证码
 */
public record ExternalEndpointAuthenticationCommand(
        String vendorCode,
        String username,
        String password,
        String authorizationCode
) {
}
