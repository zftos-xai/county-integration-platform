package cn.zqkj.platform.system.domain.vo;

/**
 * 管理端按需查看的机构HIS接入信息。
 *
 * <p>仅供具有配置写权限且具备机构数据范围的管理员在编辑时短暂使用，禁止写入日志、审计摘要或普通列表响应。</p>
 *
 * @param vendorCode 厂商编号
 * @param username 可选HIS用户名
 * @param password 可选HIS密码
 * @param authorizationCode 机构授权码
 */
public record ExternalEndpointAuthenticationVO(
        String vendorCode,
        String username,
        String password,
        String authorizationCode
) {
}
