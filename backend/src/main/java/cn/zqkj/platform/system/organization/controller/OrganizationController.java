package cn.zqkj.platform.system.organization.controller;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.organization.domain.dto.ChangeOrganizationEnabledRequest;
import cn.zqkj.platform.system.organization.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.organization.domain.dto.UpdateOrganizationCommand;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.organization.service.OrganizationAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供平台机构档案、层级和启停管理API。
 */
@Validated
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationAdministrationService service;

    /**
     * 创建机构管理控制器。
     *
     * @param service 机构授权协调服务
     */
    public OrganizationController(OrganizationAdministrationService service) {
        this.service = service;
    }

    /**
     * 查询机构列表。
     *
     * @param enabled 可选启用状态
     * @param principal 当前已登录用户
     * @return 机构记录列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('organization:read')")
    public ApiResponse<List<OrganizationVO>> findAll(
            @RequestParam(required = false) Boolean enabled,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.findAll(enabled, principal.accessActor()));
    }

    /**
     * 查询指定机构。
     *
     * @param id 机构主键
     * @param principal 当前已登录用户
     * @return 机构记录
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('organization:read')")
    public ApiResponse<OrganizationVO> get(
            @PathVariable @Positive long id,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.get(id, principal.accessActor()));
    }

    /**
     * 创建平台机构。
     *
     * @param request 创建请求
     * @param principal 当前已登录用户
     * @return 新建机构记录
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('organization:write')")
    public ApiResponse<OrganizationVO> create(
            @Valid @RequestBody CreateOrganizationCommand request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.create(request, principal.accessActor()));
    }

    /**
     * 修改平台机构。
     *
     * @param id 机构主键
     * @param request 修改请求
     * @param principal 当前已登录用户
     * @return 修改后机构记录
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('organization:write')")
    public ApiResponse<OrganizationVO> update(
            @PathVariable @Positive long id,
            @Valid @RequestBody UpdateOrganizationCommand request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.update(id, request, principal.accessActor()));
    }

    /**
     * 启用或停用平台机构。
     *
     * @param id 机构主键
     * @param request 状态修改请求
     * @param principal 当前已登录用户
     * @return 修改后机构记录
     */
    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasAuthority('organization:write')")
    public ApiResponse<OrganizationVO> setEnabled(
            @PathVariable @Positive long id,
            @Valid @RequestBody ChangeOrganizationEnabledRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.setEnabled(
                id,
                request.enabled(),
                Func.decodeRowVersion(request.version()),
                principal.accessActor()
        ));
    }

}
