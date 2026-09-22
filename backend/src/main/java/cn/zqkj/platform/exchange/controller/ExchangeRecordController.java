package cn.zqkj.platform.exchange.controller;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.exchange.domain.dto.ExchangeRecordQuery;
import cn.zqkj.platform.exchange.domain.vo.ExchangeRecordVO;
import cn.zqkj.platform.exchange.service.ExchangeRecordQueryService;
import cn.zqkj.platform.framework.security.OrganizationAccessGuard;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供按机构查询交换记录的管理 API。
 *
 * <p>入口同时要求交换记录查询权限和目标机构数据范围；查询不会触发补发。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/exchange-records")
public class ExchangeRecordController {

    private final ExchangeRecordQueryService service;
    private final OrganizationAccessGuard accessGuard;

    /**
     * 创建交换记录控制器。
     *
     * @param service 交换记录查询服务
     * @param accessGuard 机构数据范围守卫
     */
    public ExchangeRecordController(ExchangeRecordQueryService service, OrganizationAccessGuard accessGuard) {
        this.service = service;
        this.accessGuard = accessGuard;
    }

    /**
     * 查询调用者有权访问的指定机构最近交换记录。
     *
     * @param request 查询条件
     * @return 交换记录摘要列表；无数据时列表为空
     */
    @GetMapping
    @PreAuthorize("hasAuthority('exchange:read')")
    ApiResponse<List<ExchangeRecordVO>> findRecent(
            @Valid @ModelAttribute ExchangeRecordQuery request
    ) {
        accessGuard.requireAccess(request.organizationCode());
        return ApiResponse.success(service.findRecent(request));
    }
}
