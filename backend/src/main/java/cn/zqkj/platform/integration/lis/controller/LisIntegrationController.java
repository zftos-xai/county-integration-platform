package cn.zqkj.platform.integration.lis.controller;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.integration.lis.domain.dto.LisItemPackageQueryRequest;
import cn.zqkj.platform.integration.lis.domain.vo.LisItemPackageQueryResultVO;
import cn.zqkj.platform.integration.lis.service.LisIntegrationService;
import cn.zqkj.platform.system.configuration.domain.model.ExternalSystemCaller;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

/** 提供由外部系统ID/Key认证保护的LIS只读机器调用入口。 */
@RestController
@RequestMapping("/api/integration/v1/lis")
public class LisIntegrationController {

    private final LisIntegrationService service;

    /**
     * 创建县医院LIS集成入口。
     *
     * @param service LIS业务编排服务
     */
    public LisIntegrationController(LisIntegrationService service) {
        this.service = service;
    }

    /**
     * 查询指定平台机构的基层HIS LIS项目包。
     *
     * @param caller 已通过入站Key认证的外部系统身份
     * @param request 项目包查询条件
     * @return 平台操作编号、HIS交换编号和查询结果；目标结果未知或响应不可解析时使用502
     */
    @PostMapping("/item-packages/query")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LisItemPackageQueryResultVO>> queryItemPackages(
            @AuthenticationPrincipal ExternalSystemCaller caller,
            @Valid @RequestBody LisItemPackageQueryRequest request
    ) {
        String operationId = UUID.randomUUID().toString();
        LisItemPackageQueryResultVO result = service.queryItemPackages(caller, request, operationId);
        HttpStatus status = switch (result.outcome()) {
            case "NO_RESPONSE", "INVALID_RESPONSE" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(ApiResponse.success(result));
    }
}
