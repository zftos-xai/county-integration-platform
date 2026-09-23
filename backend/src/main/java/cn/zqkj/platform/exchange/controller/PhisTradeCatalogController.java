package cn.zqkj.platform.exchange.controller;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.exchange.domain.vo.PhisTradeVO;
import cn.zqkj.platform.exchange.service.PhisTradeCatalogService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供受交换记录查询权限保护的全局HIS交易目录。 */
@RestController
@RequestMapping("/api/v1/his/trades")
public class PhisTradeCatalogController {

    private final PhisTradeCatalogService service;

    /**
     * 创建交易目录控制器。
     *
     * @param service 交易目录查询服务
     */
    public PhisTradeCatalogController(PhisTradeCatalogService service) {
        this.service = service;
    }

    /**
     * 查询平台统一的HIS交易目录；目录是全局协议元数据，不按机构拆分。
     *
     * @return 交易目录
     */
    @GetMapping
    @PreAuthorize("hasAuthority('exchange:read')")
    public ApiResponse<List<PhisTradeVO>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
