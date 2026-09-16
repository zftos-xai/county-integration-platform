package cn.zqkj.platform.foundation.web.error;

import java.time.OffsetDateTime;

/**
 * 平台管理 API 的统一错误响应。
 *
 * @param code 稳定的业务错误代码
 * @param message 不包含敏感内部信息的错误说明
 * @param requestId 用于定位运行日志的请求编号
 * @param timestamp 错误响应生成时间
 */
public record ApiError(
        String code,
        String message,
        String requestId,
        OffsetDateTime timestamp
) {
}
