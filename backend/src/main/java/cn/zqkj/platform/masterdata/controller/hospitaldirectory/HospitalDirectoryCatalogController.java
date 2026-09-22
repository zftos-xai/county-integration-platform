package cn.zqkj.platform.masterdata.controller.hospitaldirectory;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.framework.security.OrganizationAccessGuard;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectoryPageVO;
import cn.zqkj.platform.masterdata.service.hospitaldirectory.HospitalDirectoryCatalogService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供当前正式医院综合目录的权限内只读API。 */
@Validated
@RestController
@RequestMapping("/api/v1/master-data/hospital-directory")
public class HospitalDirectoryCatalogController {

    private final HospitalDirectoryCatalogService service;
    private final OrganizationAccessGuard accessGuard;

    /**
     * 创建医院综合目录查询控制器。
     *
     * @param service 医院综合目录只读服务
     * @param accessGuard 显式机构筛选的入口授权守卫
     */
    public HospitalDirectoryCatalogController(
            HospitalDirectoryCatalogService service, OrganizationAccessGuard accessGuard) {
        this.service = service;
        this.accessGuard = accessGuard;
    }

    /**
     * 按有界分页条件查询医院综合目录；显式机构先校验，未指定机构时按会话范围限域。
     *
     * <p>需要 {@code master-data:read} 功能权限。</p>
     *
     * @param query 查询和分页参数
     * @return 当前有效目录分页
     */
    @GetMapping
    @PreAuthorize("hasAuthority('master-data:read')")
    public ApiResponse<HospitalDirectoryPageVO> findPage(
            @Valid @ModelAttribute HospitalDirectoryQuery query
    ) {
        return ApiResponse.success(service.findPage(
                query, accessGuard.allowedOrganizationCodes(query.organizationCode())));
    }
}
