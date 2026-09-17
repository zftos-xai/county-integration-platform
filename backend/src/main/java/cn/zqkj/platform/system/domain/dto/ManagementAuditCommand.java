package cn.zqkj.platform.system.domain.dto;

import cn.zqkj.platform.system.domain.model.AccessActor;

/**
 * 追加管理审计事件命令。
 *
 * @param actor 可选可信操作人 @param actorHint 无可信主体时的脱敏提示
 * @param organizationId 可选机构主键 @param organizationCode 可选机构代码快照
 * @param actionCode 动作代码 @param targetType 目标类型 @param targetId 目标标识
 * @param resultCode 结果代码 @param changeSummary 脱敏摘要 @param requestId 可选请求编号
 */
public record ManagementAuditCommand(AccessActor actor, String actorHint, Long organizationId,
                                     String organizationCode, String actionCode, String targetType,
                                     String targetId, String resultCode, String changeSummary, String requestId) {
}
