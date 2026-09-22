package cn.zqkj.platform.masterdata.controller.batch;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.framework.security.OrganizationAccessGuard;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.masterdata.domain.batch.dto.AdvanceMasterDataBatchRequest;
import cn.zqkj.platform.masterdata.domain.batch.dto.MasterDataBatchQuery;
import cn.zqkj.platform.masterdata.domain.batch.dto.StartMasterDataBatchRequest;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchPageVO;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataSyncOptionsVO;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectorySyncResultVO;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectorySyncResultVO;
import cn.zqkj.platform.masterdata.service.batch.MasterDataBatchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供基础数据同步批次的创建、受控取消和权限内查询API。
 */
@Validated
@RestController
@RequestMapping("/api/v1/master-data/batches")
public class MasterDataBatchController {

    private final MasterDataBatchService service;
    private final OrganizationAccessGuard accessGuard;

    /**
     * 接收同步批次操作，并使用统一机构守卫限制入口范围。
     *
     * @param service 基础数据同步批次服务
     * @param accessGuard 显式机构筛选的入口授权守卫
     */
    public MasterDataBatchController(MasterDataBatchService service,
                                     OrganizationAccessGuard accessGuard) {
        this.service = service;
        this.accessGuard = accessGuard;
    }

    /**
     * 分页查询可见同步批次；显式机构先校验，未指定机构时按会话范围限域。
     *
     * @param request 查询和分页参数
     * @return 真实分页的批次摘要
     */
    @GetMapping
    @PreAuthorize("hasAuthority('master-data:read')")
    public ApiResponse<MasterDataBatchPageVO> findPage(
            @Valid @ModelAttribute MasterDataBatchQuery request
    ) {
        return ApiResponse.success(service.findPage(
                request, accessGuard.allowedOrganizationCodes(request.organizationCode())));
    }

    /**
     * 查询当前操作人实际可用的HIS来源和同步业务。
     *
     * <p>需要 {@code master-data:sync} 功能权限。</p>
     *
     * @param principal 当前登录用户
     * @return 可实际执行的HIS来源和同步业务
     */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('master-data:sync')")
    public ApiResponse<MasterDataSyncOptionsVO> findSyncOptions(
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.findSyncOptions(List.copyOf(principal.organizationCodes())));
    }

    /**
     * 按主键读取基础数据同步批次；不存在时由调用边界按约定处理。
     *
     * <p>需要 {@code master-data:read} 功能权限。</p>
     *
     * @param id 批次主键
     * @param principal 当前登录用户
     * @return 最新批次事实
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('master-data:read')")
    public ApiResponse<MasterDataBatchSummaryVO> get(
            @PathVariable @Positive long id,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.get(id, List.copyOf(principal.organizationCodes())));
    }

    /**
     * 查询100-003同步批次按目录类型保存的运行结果。
     *
     * <p>该接口只读取已落库结果，不会再次调用HIS；历史批次没有分项证据时返回空列表。</p>
     *
     * @param id 批次主键
     * @param principal 当前登录用户
     * @return 科室、医生、病区和床位的分项处理事实
     */
    @GetMapping("/{id}/directory-results")
    @PreAuthorize("hasAuthority('master-data:read')")
    public ApiResponse<List<HospitalDirectorySyncResultVO>> findDirectoryResults(
            @PathVariable @Positive long id,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.findHospitalDirectoryResults(id, List.copyOf(principal.organizationCodes())));
    }

    /**
     * 查询100-004/100-005同步批次按中药、西药、诊疗和耗材保存的分项运行结果。
     *
     * <p>该接口只读取已落库结果，不会再次调用HIS。</p>
     *
     * @param id 批次主键
     * @param principal 当前登录用户
     * @return 医疗目录分项处理事实
     */
    @GetMapping("/{id}/medical-directory-results")
    @PreAuthorize("hasAuthority('master-data:read')")
    public ApiResponse<List<MedicalDirectorySyncResultVO>> findMedicalDirectoryResults(
            @PathVariable @Positive long id,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.findMedicalDirectoryResults(id, List.copyOf(principal.organizationCodes())));
    }

    /**
     * 在入口确认目标机构范围后创建尚未执行的同步批次。
     *
     * <p>需要 {@code master-data:sync} 功能权限。</p>
     *
     * @param request 范围明确的同步请求
     * @param principal 当前登录用户
     * @return 新建批次
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('master-data:sync')")
    public ApiResponse<MasterDataBatchSummaryVO> start(
            @Valid @RequestBody StartMasterDataBatchRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        accessGuard.requireAccess(request.organizationCode());
        return ApiResponse.success(service.start(request, principal.accessActor()));
    }

    /**
     * 在执行开始前取消同步批次。
     *
     * <p>需要 {@code master-data:sync} 功能权限。</p>
     *
     * @param id 批次主键
     * @param request 并发版本
     * @param principal 当前用户
     * @return 已取消批次
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('master-data:sync')")
    public ApiResponse<MasterDataBatchSummaryVO> cancel(
            @PathVariable @Positive long id,
            @Valid @RequestBody AdvanceMasterDataBatchRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.cancel(
                id, Func.decodeRowVersion(request.version()), principal.accessActor()));
    }

    /**
     * 按批次业务执行取数、校验和更新，并在读取批次前拒绝无效行版本。
     *
     * <p>需要 {@code master-data:sync} 功能权限。</p>
     *
     * @param id 批次主键
     * @param request 并发版本
     * @param principal 当前用户
     * @return 直接对账完成或部分异常后的同步运行
     */
    @PostMapping("/{id}/run")
    @PreAuthorize("hasAuthority('master-data:sync')")
    public ApiResponse<MasterDataBatchSummaryVO> run(
            @PathVariable @Positive long id,
            @Valid @RequestBody AdvanceMasterDataBatchRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.run(
                id, Func.decodeRowVersion(request.version()), principal.accessActor()));
    }

    /**
     * 由具备同步权限的操作人结束范围内的中断批次；保留已保存事实，不自动重试HIS。
     * @param id 批次主键
     * @param request 当前行版本
     * @param principal 当前认证用户
     * @return 回收后的批次状态
     */
    @PostMapping("/{id}/recover")
    @PreAuthorize("hasAuthority('master-data:sync')")
    public ApiResponse<MasterDataBatchSummaryVO> recover(
            @PathVariable @Positive long id,
            @Valid @RequestBody AdvanceMasterDataBatchRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal) {
        return ApiResponse.success(service.recover(
                id, Func.decodeRowVersion(request.version()), principal.accessActor()));
    }


}
