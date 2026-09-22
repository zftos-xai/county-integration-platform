package cn.zqkj.platform.system.audit.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


/**
 * 保存一次管理操作的审计事实，不携带登录会话或授权范围。
 *
 * <p>写入目标：{@code dbo.audit_management_event}。业务说明：追加管理动作、目标和结果以供追溯；发生时间由数据库生成，此输入不是完整表行，也不承载凭证正文。</p>
 *
 * @param actorUserId 已确认的用户主键；任务执行或登录失败时可为空
 * @param actorLogin 操作人名称快照；登录失败时只能使用脱敏提示
 * @param organizationId 本次操作涉及的数据所属机构主键；平台范围操作为空
 * @param organizationCode 本次操作涉及的数据所属机构代码快照；不表示操作人的所属机构
 * @param actionCode 动作代码
 * @param targetType 目标类型
 * @param targetId 目标标识
 * @param resultCode 结果代码
 * @param changeSummary 不含敏感内容的摘要
 * @param requestId 可选请求编号
 */
public record ManagementAuditCommand(
        Long actorUserId,
        @NotBlank @Size(max = 128) String actorLogin,
        Long organizationId,
        @Size(max = 64) String organizationCode,
        @NotBlank @Size(max = 64) String actionCode,
        @NotBlank @Size(max = 64) String targetType,
        @NotBlank @Size(max = 128) String targetId,
        @NotBlank @Pattern(regexp = "SUCCESS|FAILURE") String resultCode,
        @NotBlank @Size(max = 1000) String changeSummary,
        @Size(max = 64) String requestId
) {
}
