package cn.zqkj.platform.system.controller;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.vo.ManagementAuditEventVO;
import cn.zqkj.platform.system.service.ManagementAuditService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 提供按当前主体机构范围过滤的只读管理审计API。 */
@Validated
@RestController
@RequestMapping("/api/v1/audit")
public class ManagementAuditController {

    private final ManagementAuditService service;

    /** @param service 管理审计服务 */
    public ManagementAuditController(ManagementAuditService service) {
        this.service = service;
    }

    /**
     * 查询当前主体可见的管理审计事件。
     *
     * @param targetType 可选目标类型
     * @param targetId 可选目标标识
     * @param limit 最大返回条数
     * @param principal 当前主体
     * @return 时间倒序事件
     */
    @GetMapping("/events")
    @PreAuthorize("hasAuthority('audit:read')")
    public ApiResponse<List<ManagementAuditEventVO>> events(
            @RequestParam(required = false) @Size(max = 64) String targetType,
            @RequestParam(required = false) @Size(max = 128) String targetId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        AccessActor actor = new AccessActor(principal.userId(), principal.getUsername(),
                principal.organizationCodes());
        return ApiResponse.success(service.findVisible(actor, targetType, targetId, limit));
    }
}
