package cn.zqkj.platform.system.access.api;

import cn.zqkj.platform.foundation.security.PlatformUserPrincipal;
import cn.zqkj.platform.foundation.web.ApiResponse;
import cn.zqkj.platform.foundation.web.error.InvalidRequestException;
import cn.zqkj.platform.system.access.application.CreateRoleCommand;
import cn.zqkj.platform.system.access.application.PermissionView;
import cn.zqkj.platform.system.access.application.RoleAdministrationService;
import cn.zqkj.platform.system.access.application.RoleView;
import cn.zqkj.platform.system.access.application.UpdateRoleCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;

/**
 * 提供平台角色和代码注册功能权限管理API。
 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class RoleAdministrationController {

    private final RoleAdministrationService service;

    /** @param service 角色管理服务 */
    public RoleAdministrationController(RoleAdministrationService service) {
        this.service = service;
    }

    /** @return 平台角色列表 */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('access:read')")
    public ApiResponse<List<RoleView>> findRoles() {
        return ApiResponse.success(service.findAll());
    }

    /** @param roleId 角色主键 @return 角色详情 */
    @GetMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('access:read')")
    public ApiResponse<RoleView> getRole(@PathVariable @Positive long roleId) {
        return ApiResponse.success(service.get(roleId));
    }

    /** @param request 创建请求 @param principal 当前主体 @return 新角色 */
    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('access:write')")
    public ApiResponse<RoleView> createRole(
            @Valid @RequestBody CreateRoleRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.create(
                new CreateRoleCommand(request.roleCode(), request.roleName()), principal.getUsername()
        ));
    }

    /** @param roleId 角色主键 @param request 修改请求 @param principal 当前主体 @return 修改后角色 */
    @PutMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('access:write')")
    public ApiResponse<RoleView> updateRole(
            @PathVariable @Positive long roleId,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.update(roleId, new UpdateRoleCommand(
                request.roleName(), request.enabled(), decodeVersion(request.version())
        ), principal.getUsername()));
    }

    /** @param roleId 角色主键 @param request 权限请求 @param principal 当前主体 @return 修改后角色 */
    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('access:write')")
    public ApiResponse<RoleView> replacePermissions(
            @PathVariable @Positive long roleId,
            @Valid @RequestBody ReplacePermissionCodesRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.replacePermissions(
                roleId, request.permissionCodes(), principal.getUsername()
        ));
    }

    /** @return 后端代码注册权限清单 */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('access:read')")
    public ApiResponse<List<PermissionView>> findPermissions() {
        return ApiResponse.success(service.findPermissions());
    }

    /** @param value Base64版本 @return 8字节并发版本 */
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
