package cn.zqkj.platform.system.access.api;

import cn.zqkj.platform.foundation.security.PlatformUserPrincipal;
import cn.zqkj.platform.foundation.web.ApiResponse;
import cn.zqkj.platform.foundation.web.error.InvalidRequestException;
import cn.zqkj.platform.system.access.application.AccessActor;
import cn.zqkj.platform.system.access.application.CreateUserCommand;
import cn.zqkj.platform.system.access.application.ManagedUserView;
import cn.zqkj.platform.system.access.application.UpdateUserCommand;
import cn.zqkj.platform.system.access.application.UserAdministrationService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;

/**
 * 提供平台用户生命周期、角色和机构范围管理API。
 */
@Validated
@RestController
@RequestMapping("/api/v1/users")
public class UserAdministrationController {

    private final UserAdministrationService service;

    /**
     * 创建用户管理控制器。
     *
     * @param service 用户管理服务
     */
    public UserAdministrationController(UserAdministrationService service) {
        this.service = service;
    }

    /** @param principal 当前主体 @return 机构范围内用户 */
    @GetMapping
    @PreAuthorize("hasAuthority('identity:read')")
    public ApiResponse<List<ManagedUserView>> findAll(@AuthenticationPrincipal PlatformUserPrincipal principal) {
        return ApiResponse.success(service.findAll(actor(principal)));
    }

    /** @param userId 用户主键 @param principal 当前主体 @return 用户详情 */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('identity:read')")
    public ApiResponse<ManagedUserView> get(
            @PathVariable @Positive long userId,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.get(userId, actor(principal)));
    }

    /** @param request 创建请求 @param principal 当前主体 @return 新用户 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('identity:write')")
    public ApiResponse<ManagedUserView> create(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.create(new CreateUserCommand(
                request.loginName(), request.displayName(), request.primaryOrganizationId(), request.temporaryPassword()
        ), actor(principal)));
    }

    /** @param userId 用户主键 @param request 修改请求 @param principal 当前主体 @return 修改后用户 */
    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('identity:write')")
    public ApiResponse<ManagedUserView> update(
            @PathVariable @Positive long userId,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.update(userId, new UpdateUserCommand(
                request.displayName(), request.primaryOrganizationId(), decodeVersion(request.version())
        ), actor(principal)));
    }

    /** @param userId 用户主键 @param request 启停请求 @param principal 当前主体 @return 修改后用户 */
    @PatchMapping("/{userId}/enabled")
    @PreAuthorize("hasAuthority('identity:write')")
    public ApiResponse<ManagedUserView> setEnabled(
            @PathVariable @Positive long userId,
            @Valid @RequestBody ChangeEnabledRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.setEnabled(
                userId, request.enabled(), decodeVersion(request.version()), actor(principal)
        ));
    }

    /** @param userId 用户主键 @param request 重置请求 @param principal 当前主体 @return 空成功响应 */
    @PostMapping("/{userId}/password-reset")
    @PreAuthorize("hasAuthority('identity:write')")
    public ApiResponse<Void> resetPassword(
            @PathVariable @Positive long userId,
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        service.resetPassword(userId, request.temporaryPassword(), actor(principal));
        return ApiResponse.success(null);
    }

    /** @param userId 用户主键 @param request 角色请求 @param principal 当前主体 @return 修改后用户 */
    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('access:write')")
    public ApiResponse<ManagedUserView> replaceRoles(
            @PathVariable @Positive long userId,
            @Valid @RequestBody ReplaceRoleIdsRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.replaceRoles(userId, request.roleIds(), actor(principal)));
    }

    /** @param userId 用户主键 @param request 范围请求 @param principal 当前主体 @return 修改后用户 */
    @PutMapping("/{userId}/organization-scopes")
    @PreAuthorize("hasAuthority('access:write')")
    public ApiResponse<ManagedUserView> replaceOrganizationScopes(
            @PathVariable @Positive long userId,
            @Valid @RequestBody ReplaceOrganizationIdsRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.replaceOrganizations(
                userId, request.organizationIds(), actor(principal)
        ));
    }

    /** @param principal 当前主体 @return 应用服务操作人上下文 */
    private AccessActor actor(PlatformUserPrincipal principal) {
        return new AccessActor(principal.userId(), principal.getUsername(), principal.organizationCodes());
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
