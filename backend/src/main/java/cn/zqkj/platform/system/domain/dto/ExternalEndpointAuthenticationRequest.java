package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.constraints.Size;

/**
 * 管理端提交的机构HIS接口认证信息。
 *
 * <p>字段只用于写入，控制器不会通过任何查询接口回显。</p>
 *
 * @param vendorCode 厂商编号
 * @param username 可选接口用户名
 * @param password 可选接口密码
 * @param authorizationCode HIS接口验证码
 */
public record ExternalEndpointAuthenticationRequest(
        @Size(max = 100) String vendorCode,
        @Size(max = 100) String username,
        @Size(max = 200) String password,
        @Size(max = 200) String authorizationCode
) {
}
