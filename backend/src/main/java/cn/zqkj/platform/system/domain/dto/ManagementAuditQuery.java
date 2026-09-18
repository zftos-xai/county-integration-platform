package cn.zqkj.platform.system.domain.dto;

import java.time.LocalDateTime;

/**
 * 管理操作记录查询条件。
 *
 * @param actorLogin 操作人登录名，可为空
 * @param actionCode 操作代码，可为空
 * @param targetType 操作对象类型，可为空
 * @param targetId 操作对象编号，可为空
 * @param requestId 请求编号，可为空
 * @param resultCode 处理结果，可为空
 * @param occurredFrom 开始时间（UTC），可为空
 * @param occurredTo 结束时间（UTC），可为空
 * @param beforeOccurredAt 继续查询时上一页最后一条记录的时间，可为空
 * @param beforeId 继续查询时上一页最后一条记录的主键，可为空
 * @param limit 最多返回条数
 */
public record ManagementAuditQuery(String actorLogin, String actionCode, String targetType, String targetId,
                                   String requestId, String resultCode, LocalDateTime occurredFrom, LocalDateTime occurredTo,
                                   LocalDateTime beforeOccurredAt, Long beforeId, int limit) {
}
