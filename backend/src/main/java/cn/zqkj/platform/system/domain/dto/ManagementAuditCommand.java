package cn.zqkj.platform.system.domain.dto;

import cn.zqkj.platform.system.domain.model.AccessActor;

/**
 * 追加管理审计事件命令。
 *
 * @param actor 可选可信操作人 @param actorHint 无法确认登录用户时的已遮盖的提示
 * @param organizationId 本次操作涉及的数据所属机构主键；平台范围操作为空
 * @param organizationCode 本次操作涉及的数据所属机构代码快照；不表示操作人的所属机构
 * @param actionCode 动作代码 @param targetType 目标类型 @param targetId 目标标识
 * @param resultCode 结果代码 @param changeSummary 不含敏感内容的摘要 @param requestId 可选请求编号
 */
public record ManagementAuditCommand(AccessActor actor, String actorHint, Long organizationId,
                                     String organizationCode, String actionCode, String targetType,
                                     String targetId, String resultCode, String changeSummary, String requestId) {
}
