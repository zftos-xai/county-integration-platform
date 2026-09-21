package cn.zqkj.platform.masterdata.controller.hospitaldirectory;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryType;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectoryPageVO;
import cn.zqkj.platform.masterdata.service.hospitaldirectory.HospitalDirectoryCatalogService;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
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

/** 提供当前正式医院综合目录的权限内只读API。 */
@Validated
@RestController
@RequestMapping("/api/v1/master-data/hospital-directory")
public class HospitalDirectoryCatalogController {

    private final HospitalDirectoryCatalogService service;

    /**
     * 创建医院综合目录查询控制器。
     *
     * @param service 医院综合目录只读服务
     */
    public HospitalDirectoryCatalogController(HospitalDirectoryCatalogService service) {
        this.service = service;
    }

    /**
     * 按有界分页条件查询医院综合目录。
     *
     * <p>需要 {@code master-data:read} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param organizationCode 可选平台机构编码
     * @param directoryType 可选目录类型
     * @param keyword 可选编码或名称关键词
     * @param page 从1开始的页码
     * @param pageSize 页大小
     * @param principal 当前登录用户
     * @return 当前有效目录分页
     */
    @GetMapping
    @PreAuthorize("hasAuthority('master-data:read')")
    public ApiResponse<HospitalDirectoryPageVO> findPage(
            @RequestParam(required = false) @Size(max = 64) String organizationCode,
            @RequestParam(required = false) HospitalDirectoryType directoryType,
            @RequestParam(required = false) @Size(max = 50) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        HospitalDirectoryQuery query = new HospitalDirectoryQuery(
                Func.trimToNull(organizationCode), directoryType, Func.trimToNull(keyword), page, pageSize);
        AccessActor actor = new AccessActor(
                principal.userId(), principal.getUsername(), principal.organizationCodes());
        return ApiResponse.success(service.findPage(query, actor));
    }
}
