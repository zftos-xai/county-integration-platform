package cn.zqkj.platform.system.audit.controller;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditQuery;
import cn.zqkj.platform.system.audit.domain.vo.ManagementAuditEventVO;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * @param request 查询和游标参数
     * @param principal 当前用户
     * @return 时间倒序事件
     */
    @GetMapping("/events")
    @PreAuthorize("hasAuthority('audit:read')")
    public ApiResponse<List<ManagementAuditEventVO>> events(
            @Valid @ModelAttribute ManagementAuditQuery request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.findVisible(principal.organizationCodes(), request));
    }
}
