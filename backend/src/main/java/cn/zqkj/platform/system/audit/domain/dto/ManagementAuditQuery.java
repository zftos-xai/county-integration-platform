package cn.zqkj.platform.system.audit.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 接收审计事件查询的筛选项及历史游标，不包含当前用户身份。
 *
 * @param actorLogin 可选操作人登录名
 * @param actionCode 可选操作代码
 * @param targetType 可选目标类型
 * @param targetId 可选目标标识
 * @param requestId 可选平台请求编号
 * @param resultCode 可选处理结果
 * @param occurredFrom 可选UTC开始时间
 * @param occurredTo 可选UTC结束时间
 * @param beforeOccurredAt 可选历史游标时间
 * @param beforeId 可选历史游标主键
 * @param limit 最大返回条数，省略时为100
 */
public record ManagementAuditQuery(
        @Size(max = 128) String actorLogin,
        @Size(max = 64) String actionCode,
        @Size(max = 64) String targetType,
        @Size(max = 128) String targetId,
        @Size(max = 128) String requestId,
        @Pattern(regexp = "SUCCESS|FAILURE") String resultCode,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beforeOccurredAt,
        @Min(1) Long beforeId,
        @Min(1) @Max(200) Integer limit
) {

    /**
     * 统一筛选文本并补齐默认条数，约束由入口的Bean Validation执行。
     *
     * @param actorLogin 可选操作人登录名
     * @param actionCode 可选操作代码
     * @param targetType 可选目标类型
     * @param targetId 可选目标标识
     * @param requestId 可选平台请求编号
     * @param resultCode 可选处理结果
     * @param occurredFrom 可选UTC开始时间
     * @param occurredTo 可选UTC结束时间
     * @param beforeOccurredAt 可选历史游标时间
     * @param beforeId 可选历史游标主键
     * @param limit 最大返回条数，省略时为100
     */
    public ManagementAuditQuery {
        actorLogin = Func.trimToNull(actorLogin);
        actionCode = Func.trimToNull(actionCode);
        targetType = Func.trimToNull(targetType);
        targetId = Func.trimToNull(targetId);
        requestId = Func.trimToNull(requestId);
        resultCode = Func.trimToNull(resultCode);
        limit = limit == null ? 100 : limit;
    }

    /**
     * 检查查询起止时间的先后关系。
     * @return 未限定区间或下界不晚于上界时为true
     */
    @AssertTrue(message = "开始时间不能晚于结束时间")
    public boolean isTimeRangeValid() {
        return occurredFrom == null || occurredTo == null || !occurredFrom.isAfter(occurredTo);
    }

    /**
     * 保证历史游标的时间和主键同时提供。
     * @return 游标完整或未提供游标时为true
     */
    @AssertTrue(message = "历史游标必须同时提供时间和主键")
    public boolean isCursorComplete() {
        return (beforeOccurredAt == null) == (beforeId == null);
    }
}
