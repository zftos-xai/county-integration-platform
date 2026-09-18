package cn.zqkj.platform.system.controller;

import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.system.domain.dto.CreateRoleCommand;
import cn.zqkj.platform.system.domain.dto.CreateRoleRequest;
import cn.zqkj.platform.system.domain.dto.DeleteRoleRequest;
import cn.zqkj.platform.system.domain.dto.ReplacePermissionCodesRequest;
import cn.zqkj.platform.system.domain.dto.UpdateRoleCommand;
import cn.zqkj.platform.system.domain.dto.UpdateRoleRequest;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.vo.PermissionVO;
import cn.zqkj.platform.system.domain.vo.RoleVO;
import cn.zqkj.platform.system.service.RoleAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    public ApiResponse<List<RoleVO>> findRoles() {
        return ApiResponse.success(service.findAll());
    }

    /** @param roleId 角色主键 @return 角色详情 */
    @GetMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('access:read')")
    public ApiResponse<RoleVO> getRole(@PathVariable @Positive long roleId) {
        return ApiResponse.success(service.get(roleId));
    }

    /** @param request 创建请求 @param principal 当前用户 @return 新角色 */
    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('access:write')")
    public ApiResponse<RoleVO> createRole(
            @Valid @RequestBody CreateRoleRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.create(
                new CreateRoleCommand(request.roleCode(), request.roleName()), actor(principal)
        ));
    }

    /** @param roleId 角色主键 @param request 修改请求 @param principal 当前用户 @return 修改后角色 */
    @PutMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('access:write')")
    public ApiResponse<RoleVO> updateRole(
            @PathVariable @Positive long roleId,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.update(roleId, new UpdateRoleCommand(
                request.roleName(), request.enabled(), decodeVersion(request.version())
        ), actor(principal)));
    }

    /** @param roleId 角色主键 @param request 删除请求 @param principal 当前用户 @return 空成功响应 */
    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('access:write')")
    public ApiResponse<Void> deleteRole(
            @PathVariable @Positive long roleId,
            @Valid @RequestBody DeleteRoleRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        service.delete(roleId, decodeVersion(request.version()), actor(principal));
        return ApiResponse.success(null);
    }

    /** @param roleId 角色主键 @param request 权限请求 @param principal 当前用户 @return 修改后角色 */
    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('access:write')")
    public ApiResponse<RoleVO> replacePermissions(
            @PathVariable @Positive long roleId,
            @Valid @RequestBody ReplacePermissionCodesRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.replacePermissions(
                roleId, request.permissionCodes(), actor(principal)
        ));
    }

    /** @return 后端代码注册权限清单 */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('access:read')")
    public ApiResponse<List<PermissionVO>> findPermissions() {
        return ApiResponse.success(service.findPermissions());
    }

    /** @param principal 当前用户 @return 审计操作人 */
    private AccessActor actor(PlatformUserPrincipal principal) {
        return new AccessActor(principal.userId(), principal.getUsername(), principal.organizationCodes());
    }

    /** @param value Base64版本 @return 8字节并发版本 */
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
