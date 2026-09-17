package cn.zqkj.platform.system.domain.model;

import java.time.LocalDateTime;

/**
 * 只读管理审计事件快照。
 *
 * @param id 主键 @param occurredAt 发生UTC时间 @param actorUserId 可选操作人主键
 * @param actorLoginSnapshot 操作人快照 @param organizationId 可选机构主键
 * @param organizationCodeSnapshot 可选机构代码快照 @param actionCode 动作代码
 * @param targetType 目标类型 @param targetId 目标标识 @param resultCode 结果代码
 * @param changeSummary 脱敏摘要 @param requestId 可选请求编号
 */
public record ManagementAuditEvent(long id, LocalDateTime occurredAt, Long actorUserId,
                                   String actorLoginSnapshot, Long organizationId,
                                   String organizationCodeSnapshot, String actionCode, String targetType,
                                   String targetId, String resultCode, String changeSummary, String requestId) {
}
