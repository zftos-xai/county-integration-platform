package cn.zqkj.platform.masterdata.controller.icd10;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.masterdata.domain.icd10.dto.Icd10DirectoryQuery;
import cn.zqkj.platform.masterdata.domain.icd10.vo.Icd10DirectoryPageVO;
import cn.zqkj.platform.masterdata.service.icd10.Icd10DirectoryCatalogService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供平台公共ICD10当前目录的只读API。 */
@Validated
@RestController
@RequestMapping("/api/v1/master-data/icd10-directory")
public class Icd10DirectoryCatalogController {
    private final Icd10DirectoryCatalogService service;

    /** @param service 公共ICD10目录只读服务。 */
    public Icd10DirectoryCatalogController(Icd10DirectoryCatalogService service) {
        this.service = service;
    }

    /**
     * 查询已发布公共目录，不触发HIS调用。
     *
     * @param query 查询和分页参数
     * @return 公共ICD10目录分页
     */
    @GetMapping
    @PreAuthorize("hasAuthority('master-data:read')")
    public ApiResponse<Icd10DirectoryPageVO> findPage(
            @Valid @ModelAttribute Icd10DirectoryQuery query
    ) {
        return ApiResponse.success(service.findPage(query));
    }
}
