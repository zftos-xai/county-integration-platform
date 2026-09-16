package cn.zqkj.platform.modules.organization.api;

import cn.zqkj.platform.foundation.web.ApiResponse;
import cn.zqkj.platform.foundation.security.PlatformUserPrincipal;
import cn.zqkj.platform.foundation.web.error.InvalidRequestException;
import cn.zqkj.platform.modules.organization.application.CreateOrganizationCommand;
import cn.zqkj.platform.modules.organization.application.OrganizationView;
import cn.zqkj.platform.modules.organization.application.UpdateOrganizationCommand;
import cn.zqkj.platform.system.access.application.AccessActor;
import cn.zqkj.platform.system.access.application.OrganizationAdministrationService;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
     * @param principal 当前认证主体
     * @return 机构快照列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('organization:read')")
    public ApiResponse<List<OrganizationView>> findAll(
            @RequestParam(required = false) Boolean enabled,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.findAll(enabled, actor(principal)));
    }

    /**
     * 查询指定机构。
     *
     * @param id 机构主键
     * @param principal 当前认证主体
     * @return 机构快照
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('organization:read')")
    public ApiResponse<OrganizationView> get(
            @PathVariable @Positive long id,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.get(id, actor(principal)));
    }

    /**
     * 创建平台机构。
     *
     * @param request 创建请求
     * @param principal 当前已认证主体
     * @return 新建机构快照
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('organization:write')")
    public ApiResponse<OrganizationView> create(
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        CreateOrganizationCommand command = new CreateOrganizationCommand(
                request.organizationCode(),
                request.organizationName(),
                request.organizationType(),
                request.parentId(),
                toUtc(request.validFrom()),
                toUtc(request.validTo())
        );
        return ApiResponse.success(service.create(command, actor(principal)));
    }

    /**
     * 修改平台机构。
     *
     * @param id 机构主键
     * @param request 修改请求
     * @param principal 当前已认证主体
     * @return 修改后机构快照
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('organization:write')")
    public ApiResponse<OrganizationView> update(
            @PathVariable @Positive long id,
            @Valid @RequestBody UpdateOrganizationRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        UpdateOrganizationCommand command = new UpdateOrganizationCommand(
                request.organizationName(),
                request.organizationType(),
                request.parentId(),
                toUtc(request.validFrom()),
                toUtc(request.validTo()),
                decodeVersion(request.version())
        );
        return ApiResponse.success(service.update(id, command, actor(principal)));
    }

    /**
     * 启用或停用平台机构。
     *
     * @param id 机构主键
     * @param request 状态修改请求
     * @param principal 当前已认证主体
     * @return 修改后机构快照
     */
    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasAuthority('organization:write')")
    public ApiResponse<OrganizationView> setEnabled(
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

    /** @param principal 当前主体 @return 应用服务操作人上下文 */
    private AccessActor actor(PlatformUserPrincipal principal) {
        return new AccessActor(principal.userId(), principal.getUsername(), principal.organizationCodes());
    }

    /**
     * 将API带偏移时间转换为数据库使用的UTC本地时间。
     *
     * @param value 可选带偏移时间
     * @return UTC本地时间；输入为空时返回空
     */
    private java.time.LocalDateTime toUtc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
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
                throw new InvalidRequestException("version must represent an 8-byte rowversion value");
            }
            return version;
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("version must be valid Base64");
        }
    }
}
