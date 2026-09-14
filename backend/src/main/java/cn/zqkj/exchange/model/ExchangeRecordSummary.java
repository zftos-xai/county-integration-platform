package cn.zqkj.exchange.model;

import java.time.LocalDateTime;

public record ExchangeRecordSummary(
        long id,
        String requestId,
        String interfaceCode,
        String organizationCode,
        String sourceRecordId,
        String status,
        LocalDateTime receivedAt,
        LocalDateTime processedAt
) {
}
