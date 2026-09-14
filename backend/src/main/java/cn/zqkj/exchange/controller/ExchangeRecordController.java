package cn.zqkj.exchange.controller;

import cn.zqkj.access.OrganizationAccessGuard;
import cn.zqkj.exchange.model.ExchangeRecordSummary;
import cn.zqkj.exchange.service.ExchangeQueryService;
import cn.zqkj.shared.api.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/exchange-records")
public class ExchangeRecordController {

    private final ExchangeQueryService service;
    private final OrganizationAccessGuard accessGuard;

    public ExchangeRecordController(ExchangeQueryService service, OrganizationAccessGuard accessGuard) {
        this.service = service;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    ApiResponse<List<ExchangeRecordSummary>> findRecent(
            @RequestParam @NotBlank String organizationCode,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            Authentication authentication
    ) {
        accessGuard.requireAccess(authentication, organizationCode);
        return ApiResponse.success(service.findRecent(organizationCode, limit));
    }
}
