package cn.zqkj.platform.system.domain.vo;

/**
 * 表示管理端提交写请求所需的CSRF令牌元数据。
 *
 * @param headerName 请求头名称
 * @param parameterName 表单参数名称
 * @param token 当前令牌
 */
public record CsrfTokenVO(String headerName, String parameterName, String token) {
}
