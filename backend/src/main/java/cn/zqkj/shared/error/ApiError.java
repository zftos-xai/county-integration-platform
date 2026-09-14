package cn.zqkj.shared.error;

import java.time.OffsetDateTime;

public record ApiError(
        String code,
        String message,
        String requestId,
        OffsetDateTime timestamp
) {
}
