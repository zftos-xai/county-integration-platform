package cn.zqkj.platform.system.controller;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.domain.dto.ManagementAuditQuery;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.vo.ManagementAuditEventVO;
import cn.zqkj.platform.system.service.ManagementAuditService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** 提供按当前用户机构范围过滤的只读管理审计API。 */
@Validated
@RestController
@RequestMapping("/api/v1/audit")
public class ManagementAuditController {

    private final ManagementAuditService service;

    /**
     * 创建管理审计查询控制器。
     *
     * @param service 管理审计服务
     */
    public ManagementAuditController(ManagementAuditService service) {
        this.service = service;
    }

    /**
     * 查询当前用户可见的管理审计事件。
     *
     * @param actorLogin 可选操作人登录名
     * @param actionCode 可选操作代码
     * @param targetType 可选目标类型
     * @param targetId 可选目标标识
     * @param requestId 可选请求编号
     * @param resultCode 可选处理结果
     * @param occurredFrom 可选开始时间（UTC）
     * @param occurredTo 可选结束时间（UTC）
     * @param beforeOccurredAt 继续查询时上一页最后一条记录的时间
     * @param beforeId 继续查询时上一页最后一条记录的主键
     * @param limit 最大返回条数
     * @param principal 当前用户
     * @return 时间倒序事件
     */
    @GetMapping("/events")
    @PreAuthorize("hasAuthority('audit:read')")
    public ApiResponse<List<ManagementAuditEventVO>> events(
            @RequestParam(required = false) @Size(max = 128) String actorLogin,
            @RequestParam(required = false) @Size(max = 64) String actionCode,
            @RequestParam(required = false) @Size(max = 64) String targetType,
            @RequestParam(required = false) @Size(max = 128) String targetId,
            @RequestParam(required = false) @Size(max = 128) String requestId,
            @RequestParam(required = false) @Size(max = 16) String resultCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime occurredTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime beforeOccurredAt,
            @RequestParam(required = false) @Min(1) Long beforeId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        AccessActor actor = new AccessActor(principal.userId(), principal.getUsername(),
                principal.organizationCodes());
        ManagementAuditQuery query = new ManagementAuditQuery(actorLogin, actionCode, targetType, targetId,
                requestId, resultCode, occurredFrom, occurredTo, beforeOccurredAt, beforeId, limit);
        return ApiResponse.success(service.findVisible(actor, query));
    }
}
