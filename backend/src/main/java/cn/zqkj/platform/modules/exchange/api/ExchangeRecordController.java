package cn.zqkj.platform.modules.exchange.api;

import cn.zqkj.platform.foundation.security.OrganizationAccessGuard;
import cn.zqkj.platform.foundation.web.ApiResponse;
import cn.zqkj.platform.modules.exchange.application.ExchangeRecordQuery;
import cn.zqkj.platform.modules.exchange.application.ExchangeRecordQueryService;
import cn.zqkj.platform.modules.exchange.application.ExchangeRecordSummary;
import cn.zqkj.platform.modules.exchange.application.ExchangeResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 提供按机构查询交换记录的管理 API。
 *
 * <p>在调用应用服务前执行机构数据范围校验，查询本身不会触发补发。</p>
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
     * @param organizationCode 平台统一机构代码
     * @param receivedFrom 接收时间下界，按 UTC 时间解释
     * @param receivedTo 接收时间上界，按 UTC 时间解释
     * @param interfaceCode 可选接口事件码或交易码
     * @param sourceRecordId 可选业务记录引用
     * @param requestId 可选平台请求编号
     * @param result 可选最终交换结果
     * @param limit 最大返回数量，取值范围为 1 至 100
     * @param authentication 当前登录主体
     * @return 交换记录摘要列表；无数据时列表为空
     */
    @GetMapping
    ApiResponse<List<ExchangeRecordSummary>> findRecent(
            @RequestParam @NotBlank @Size(max = 64) String organizationCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime receivedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime receivedTo,
            @RequestParam(required = false) @Size(max = 64) String interfaceCode,
            @RequestParam(required = false) @Size(max = 128) String sourceRecordId,
            @RequestParam(required = false) @Size(max = 64) String requestId,
            @RequestParam(required = false) ExchangeResult result,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            Authentication authentication
    ) {
        accessGuard.requireAccess(authentication, organizationCode);
        ExchangeRecordQuery query = new ExchangeRecordQuery(
                organizationCode,
                toUtcLocalDateTime(receivedFrom),
                toUtcLocalDateTime(receivedTo),
                interfaceCode,
                sourceRecordId,
                requestId,
                result,
                limit
        );
        return ApiResponse.success(service.findRecent(query));
    }

    /**
     * 将带偏移量的 API 时间统一转换为数据库使用的 UTC 本地时间。
     *
     * @param value 调用方提供的带偏移时间；为空表示不限制该侧边界
     * @return UTC 本地时间，或在输入为空时返回空
     */
    private java.time.LocalDateTime toUtcLocalDateTime(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
