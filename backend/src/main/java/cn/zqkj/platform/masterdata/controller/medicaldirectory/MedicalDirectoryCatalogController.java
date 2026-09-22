package cn.zqkj.platform.masterdata.controller.medicaldirectory;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.framework.security.OrganizationAccessGuard;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectoryPageVO;
import cn.zqkj.platform.masterdata.service.medicaldirectory.MedicalDirectoryCatalogService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供当前正式医疗目录的权限内只读API。
 */
@Validated
@RestController
@RequestMapping("/api/v1/master-data/medical-directory")
public class MedicalDirectoryCatalogController {

    private final MedicalDirectoryCatalogService service;
    private final OrganizationAccessGuard accessGuard;

    /**
     * 创建医疗目录查询控制器。
     *
     * @param service 医疗目录只读服务
     * @param accessGuard 显式机构筛选的入口授权守卫
     */
    public MedicalDirectoryCatalogController(
            MedicalDirectoryCatalogService service, OrganizationAccessGuard accessGuard) {
        this.service = service;
        this.accessGuard = accessGuard;
    }

    /**
     * 按有界分页条件查询医疗目录；显式机构先校验，未指定机构时按会话范围限域。
     *
     * @param query 查询和分页参数
     * @return 当前有效医疗目录分页
     */
    @GetMapping
    @PreAuthorize("hasAuthority('master-data:read')")
    public ApiResponse<MedicalDirectoryPageVO> findPage(
            @Valid @ModelAttribute MedicalDirectoryQuery query
    ) {
        return ApiResponse.success(service.findPage(
                query, accessGuard.allowedOrganizationCodes(query.organizationCode())));
    }
}
