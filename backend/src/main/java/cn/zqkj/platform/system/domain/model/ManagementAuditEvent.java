package cn.zqkj.platform.system.domain.model;

import java.time.LocalDateTime;

/**
 * 只读管理审计事件快照。
 *
 * @param id 主键 @param occurredAt 发生UTC时间 @param actorUserId 可选操作人主键
 * @param actorLoginSnapshot 操作时记录的用户信息
 * @param organizationId 本次操作涉及的数据所属机构主键；平台范围操作为空
 * @param organizationCodeSnapshot 本次操作涉及的数据所属机构代码快照；不表示操作人的所属机构
 * @param actionCode 动作代码
 * @param targetType 目标类型 @param targetId 目标标识 @param resultCode 结果代码
 * @param changeSummary 不含敏感内容的摘要 @param requestId 可选请求编号
 */
public record ManagementAuditEvent(long id, LocalDateTime occurredAt, Long actorUserId,
                                   String actorLoginSnapshot, Long organizationId,
                                   String organizationCodeSnapshot, String actionCode, String targetType,
                                   String targetId, String resultCode, String changeSummary, String requestId) {
}
