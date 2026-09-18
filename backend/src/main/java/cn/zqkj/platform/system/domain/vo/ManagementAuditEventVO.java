package cn.zqkj.platform.system.domain.vo;

import java.time.LocalDateTime;

/**
 * 管理审计事件安全输出。
 *
 * @param id 主键 @param occurredAt 发生UTC时间 @param actorLogin 操作时记录的用户信息
 * @param organizationCode 本次操作涉及的数据所属机构代码；平台范围操作为空，不表示操作人的所属机构
 * @param actionCode 动作代码 @param targetType 目标类型
 * @param targetId 目标标识 @param resultCode 结果代码 @param changeSummary 不含敏感内容的摘要
 * @param requestId 可选请求编号
 */
public record ManagementAuditEventVO(long id, LocalDateTime occurredAt, String actorLogin,
                                     String organizationCode, String actionCode, String targetType,
                                     String targetId, String resultCode, String changeSummary, String requestId) {
}
