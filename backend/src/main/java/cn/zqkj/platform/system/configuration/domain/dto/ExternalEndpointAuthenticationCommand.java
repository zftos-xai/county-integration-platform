package cn.zqkj.platform.system.configuration.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import jakarta.validation.constraints.Size;

/**
 * 管理端提交的机构HIS接口认证信息。
 *
 * <p>认证字段不得进入日志或审计；只有专用的禁止缓存接口允许授权查看。</p>
 *
 * @param vendorCode 厂商编号
 * @param username 可选接口用户名
 * @param password 可选接口密码
 * @param authorizationCode HIS接口验证码
 */
public record ExternalEndpointAuthenticationCommand(
        @Size(max = 100) String vendorCode,
        @Size(max = 100) String username,
        @Size(max = 200) String password,
        @Size(max = 200) String authorizationCode
) {
    /**
     * 裁剪提交值，空白认证字段在修改时表示保留已有值。
     *
     * @param vendorCode 厂商编号
     * @param username 可选接口用户名
     * @param password 可选接口密码
     * @param authorizationCode HIS接口验证码
     */
    public ExternalEndpointAuthenticationCommand {
        vendorCode = Func.trimToNull(vendorCode);
        username = Func.trimToNull(username);
        password = Func.trimToNull(password);
        authorizationCode = Func.trimToNull(authorizationCode);
    }
}
