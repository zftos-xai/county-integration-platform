package cn.zqkj.platform.exchange.domain.dto;

import cn.zqkj.platform.exchange.domain.model.ExchangeResult;

import java.time.LocalDateTime;

/**
 * 交换运行记录的只读查询条件。
 *
 * @param organizationCode 平台统一机构代码，始终作为强制数据范围条件
 * @param receivedFrom 接收时间下界，按 UTC 时间解释；为空时不限制下界
 * @param receivedTo 接收时间上界，按 UTC 时间解释；为空时不限制上界
 * @param interfaceCode 接口事件码或交易码；为空时不筛选
 * @param sourceRecordId 不包含患者正文的业务记录引用；为空时不筛选
 * @param requestId 平台请求编号；为空时不筛选
 * @param result 三类最终交换结果之一；为空时不筛选
 * @param limit 最大返回数量，应用服务将其限制在 1 至 100
 */
public record ExchangeRecordQuery(
        String organizationCode,
        LocalDateTime receivedFrom,
        LocalDateTime receivedTo,
        String interfaceCode,
        String sourceRecordId,
        String requestId,
        ExchangeResult result,
        int limit
) {
}
