package cn.zqkj.platform.system.controller;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.system.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.domain.dto.CreateOrganizationRequest;
import cn.zqkj.platform.system.domain.dto.ChangeOrganizationEnabledRequest;
import cn.zqkj.platform.system.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.domain.dto.UpdateOrganizationCommand;
import cn.zqkj.platform.system.domain.dto.UpdateOrganizationRequest;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.service.OrganizationAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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

import java.util.Base64;
import java.util.List;

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
        return ApiResponse.success(service.findAll(enabled, actor(principal)));
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
        return ApiResponse.success(service.get(id, actor(principal)));
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
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        CreateOrganizationCommand command = new CreateOrganizationCommand(
                request.organizationCode(),
                request.organizationName(),
                request.organizationType(),
                request.parentId(),
                Func.toUtc(request.validFrom()),
                Func.toUtc(request.validTo())
        );
        return ApiResponse.success(service.create(command, actor(principal)));
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
            @Valid @RequestBody UpdateOrganizationRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        UpdateOrganizationCommand command = new UpdateOrganizationCommand(
                request.organizationName(),
                request.organizationType(),
                request.parentId(),
                Func.toUtc(request.validFrom()),
                Func.toUtc(request.validTo()),
                decodeVersion(request.version())
        );
        return ApiResponse.success(service.update(id, command, actor(principal)));
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
                decodeVersion(request.version()),
                actor(principal)
        ));
    }

    /** @param principal 当前用户 @return 应用服务操作人信息 */
    private AccessActor actor(PlatformUserPrincipal principal) {
        return new AccessActor(principal.userId(), principal.getUsername(), principal.organizationCodes());
    }

    /**
     * 解析Base64编码的SQL Server并发版本。
     *
     * @param value Base64编码版本
     * @return 8字节并发版本
     */
    private byte[] decodeVersion(String value) {
        try {
            byte[] version = Base64.getDecoder().decode(value);
            if (version.length != Long.BYTES) {
                throw new InvalidRequestException("version 必须表示一个 8 字节的 SQL Server 行版本号");
            }
            return version;
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("version 必须是有效的 Base64 文本");
        }
    }
}
