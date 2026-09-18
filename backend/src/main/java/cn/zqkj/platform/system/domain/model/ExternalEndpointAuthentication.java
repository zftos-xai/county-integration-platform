package cn.zqkj.platform.system.domain.model;

/**
 * 外部系统运行时使用的机构认证信息。
 *
 * <p>该对象只允许在一次外部调用期间使用，不得写入日志、审计摘要或API响应。</p>
 *
 * @param vendorCode 厂商编号
 * @param username 可选接口用户名
 * @param password 可选接口密码
 * @param authorizationCode 机构授权码
 */
public record ExternalEndpointAuthentication(
        String vendorCode,
        String username,
        String password,
        String authorizationCode
) {
}
