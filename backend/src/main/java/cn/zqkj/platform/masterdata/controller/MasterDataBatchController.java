package cn.zqkj.platform.masterdata.controller;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.masterdata.domain.dto.AdvanceMasterDataBatchRequest;
import cn.zqkj.platform.masterdata.domain.dto.MasterDataBatchQuery;
import cn.zqkj.platform.masterdata.domain.dto.StartMasterDataBatchRequest;
import cn.zqkj.platform.masterdata.domain.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.vo.MasterDataBatchPageVO;
import cn.zqkj.platform.masterdata.domain.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.masterdata.domain.vo.HospitalDirectorySyncResultVO;
import cn.zqkj.platform.masterdata.domain.vo.MasterDataSyncOptionsVO;
import cn.zqkj.platform.masterdata.service.HospitalDirectorySyncService;
import cn.zqkj.platform.masterdata.service.MasterDataBatchService;
import cn.zqkj.platform.system.domain.model.AccessActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 提供基础数据同步批次的创建、受控取消和权限内查询API。
 */
@Validated
@RestController
@RequestMapping("/api/v1/master-data/batches")
public class MasterDataBatchController {

    private final MasterDataBatchService service;
    private final HospitalDirectorySyncService hospitalDirectorySyncService;

    /**
     * 创建基础数据同步批次控制器并注入批次与医院目录同步服务。
     *
     * @param service 基础数据同步批次服务
     * @param hospitalDirectorySyncService 100-003医院综合目录同步服务
     */
    public MasterDataBatchController(MasterDataBatchService service,
                                     HospitalDirectorySyncService hospitalDirectorySyncService) {
        this.service = service;
        this.hospitalDirectorySyncService = hospitalDirectorySyncService;
    }

    /**
     * 分页查询当前用户可见的同步批次。
     *
     * @param organizationCode 可选平台机构代码
     * @param requestKey 可选请求标识
     * @param category 可选数据类别
     * @param status 可选批次状态
     * @param startedFrom 可选批次开始时间下界
     * @param startedTo 可选批次开始时间上界
     * @param page 从1开始的页码
     * @param pageSize 页大小
     * @param principal 当前登录用户
     * @return 真实分页的批次摘要
     */
    @GetMapping
    @PreAuthorize("hasAuthority('master-data:read')")
    public ApiResponse<MasterDataBatchPageVO> findPage(
            @RequestParam(required = false) @Size(max = 64) String organizationCode,
            @RequestParam(required = false) @Size(max = 64) String requestKey,
            @RequestParam(required = false) MasterDataCategory category,
            @RequestParam(required = false) MasterDataBatchStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startedTo,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        MasterDataBatchQuery query = new MasterDataBatchQuery(
                Func.trimToNull(organizationCode),
                Func.trimToNull(requestKey),
                category,
                status,
                Func.toUtc(startedFrom),
                Func.toUtc(startedTo),
                page,
                pageSize
        );
        return ApiResponse.success(service.findPage(query, actor(principal)));
    }

    /**
     * 查询当前操作人实际可用的HIS来源和同步业务。
     *
     * <p>需要 {@code master-data:sync} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param principal 当前登录用户
     * @return 可实际执行的HIS来源和同步业务
     */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('master-data:sync')")
    public ApiResponse<MasterDataSyncOptionsVO> findSyncOptions(
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.findSyncOptions(actor(principal)));
    }

    /**
     * 按主键读取基础数据同步批次；不存在时由调用边界按约定处理。
     *
     * <p>需要 {@code master-data:read} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
        return ApiResponse.success(service.get(id, actor(principal)));
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
        return ApiResponse.success(service.findHospitalDirectoryResults(id, actor(principal)));
    }

    /**
     * 创建尚未执行的基础数据同步批次。
     *
     * <p>需要 {@code master-data:sync} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
        return ApiResponse.success(service.start(request, actor(principal)));
    }

    /**
     * 在执行开始前取消维护方案。
     *
     * <p>需要 {@code master-data:sync} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
                id, decodeVersion(request.version()), actor(principal)));
    }

    /**
     * 执行医院综合目录的完整取数、校验和对账流程。
     *
     * <p>需要 {@code master-data:sync} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param id 批次主键
     * @param request 并发版本
     * @param principal 当前用户
     * @return 直接对账完成或部分异常后的同步运行
     */
    @PostMapping("/{id}/run")
    @PreAuthorize("hasAuthority('master-data:sync')")
    public ApiResponse<MasterDataBatchSummaryVO> runHospitalDirectory(
            @PathVariable @Positive long id,
            @Valid @RequestBody AdvanceMasterDataBatchRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(hospitalDirectorySyncService.fetchValidateAndReconcile(
                id, decodeVersion(request.version()), actor(principal)));
    }

    /**
     * 解码并校验客户端提交的SQL Server行版本。
     *
     * @param encoded Base64数据库并发版本
     * @return 解码后的版本
     */
    private byte[] decodeVersion(String encoded) {
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new cn.zqkj.platform.common.exception.InvalidRequestException("批次版本格式无效");
        }
    }

    /**
     * 将当前登录主体转换为携带机构范围的服务层操作人。
     *
     * @param principal 当前用户
     * @return 服务层操作人
     */
    private AccessActor actor(PlatformUserPrincipal principal) {
        return new AccessActor(principal.userId(), principal.getUsername(), principal.organizationCodes());
    }

}
