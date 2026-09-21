package cn.zqkj.platform.system.identity.controller;

import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.identity.domain.dto.ChangeEnabledRequest;
import cn.zqkj.platform.system.identity.domain.dto.CreateUserCommand;
import cn.zqkj.platform.system.identity.domain.dto.CreateUserRequest;
import cn.zqkj.platform.system.identity.domain.dto.ReplaceOrganizationIdsRequest;
import cn.zqkj.platform.system.identity.domain.dto.ReplaceRoleIdsRequest;
import cn.zqkj.platform.system.identity.domain.dto.ResetPasswordRequest;
import cn.zqkj.platform.system.identity.domain.vo.ManagedUserVO;
import cn.zqkj.platform.system.identity.domain.dto.UpdateUserCommand;
import cn.zqkj.platform.system.identity.domain.dto.UpdateUserRequest;
import cn.zqkj.platform.system.identity.service.UserAdministrationService;
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

    /**
     * 查询当前调用方可见的平台用户列表。
     *
     * <p>需要 {@code identity:read} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param principal 当前用户
     * @return 机构范围内用户
     */
    @GetMapping
    @PreAuthorize("hasAuthority('identity:read')")
    public ApiResponse<List<ManagedUserVO>> findAll(@AuthenticationPrincipal PlatformUserPrincipal principal) {
        return ApiResponse.success(service.findAll(actor(principal)));
    }

    /**
     * 按主键读取平台用户；不存在时由调用边界按约定处理。
     *
     * <p>需要 {@code identity:read} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param userId 用户主键
     * @param principal 当前用户
     * @return 用户详情
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('identity:read')")
    public ApiResponse<ManagedUserVO> get(
            @PathVariable @Positive long userId,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.get(userId, actor(principal)));
    }

    /**
     * 创建平台用户并返回最新视图。
     *
     * <p>需要 {@code identity:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param request 创建请求
     * @param principal 当前用户
     * @return 新用户
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('identity:write')")
    public ApiResponse<ManagedUserVO> create(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.create(new CreateUserCommand(
                request.loginName(), request.displayName(), request.primaryOrganizationId(), request.temporaryPassword()
        ), actor(principal)));
    }

    /**
     * 按并发版本更新平台用户并返回最新视图。
     *
     * <p>需要 {@code identity:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param userId 用户主键
     * @param request 修改请求
     * @param principal 当前用户
     * @return 修改后用户
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('identity:write')")
    public ApiResponse<ManagedUserVO> update(
            @PathVariable @Positive long userId,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.update(userId, new UpdateUserCommand(
                request.displayName(), request.primaryOrganizationId(), decodeVersion(request.version())
        ), actor(principal)));
    }

    /**
     * 按并发版本修改平台用户的启用状态。
     *
     * <p>需要 {@code identity:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param userId 用户主键
     * @param request 启停请求
     * @param principal 当前用户
     * @return 修改后用户
     */
    @PatchMapping("/{userId}/enabled")
    @PreAuthorize("hasAuthority('identity:write')")
    public ApiResponse<ManagedUserVO> setEnabled(
            @PathVariable @Positive long userId,
            @Valid @RequestBody ChangeEnabledRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.setEnabled(
                userId, request.enabled(), decodeVersion(request.version()), actor(principal)
        ));
    }

    /**
     * 重置用户密码并要求其下次登录后修改密码。
     *
     * <p>需要 {@code identity:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param userId 用户主键
     * @param request 重置请求
     * @param principal 当前用户
     * @return 空成功响应
     */
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

    /**
     * 整体替换用户角色集合。
     *
     * <p>需要 {@code access:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param userId 用户主键
     * @param request 角色请求
     * @param principal 当前用户
     * @return 修改后用户
     */
    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('access:write')")
    public ApiResponse<ManagedUserVO> replaceRoles(
            @PathVariable @Positive long userId,
            @Valid @RequestBody ReplaceRoleIdsRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.replaceRoles(userId, request.roleIds(), actor(principal)));
    }

    /**
     * 整体替换用户的机构数据范围。
     *
     * <p>需要 {@code access:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param userId 用户主键
     * @param request 范围请求
     * @param principal 当前用户
     * @return 修改后用户
     */
    @PutMapping("/{userId}/organization-scopes")
    @PreAuthorize("hasAuthority('access:write')")
    public ApiResponse<ManagedUserVO> replaceOrganizationScopes(
            @PathVariable @Positive long userId,
            @Valid @RequestBody ReplaceOrganizationIdsRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.replaceOrganizations(
                userId, request.organizationIds(), actor(principal)
        ));
    }

    /**
     * 将当前登录主体转换为携带机构范围的服务层操作人。
     *
     * @param principal 当前用户
     * @return 应用服务操作人信息
     */
    private AccessActor actor(PlatformUserPrincipal principal) {
        return new AccessActor(principal.userId(), principal.getUsername(), principal.organizationCodes());
    }

    /**
     * 解码并校验客户端提交的SQL Server行版本。
     *
     * @param value Base64版本
     * @return 8字节并发版本
     */
    private byte[] decodeVersion(String value) {
        try {
            byte[] version = Func.decodeBase64(value);
            if (version.length != Long.BYTES) {
                throw new InvalidRequestException("version 必须表示一个 8 字节的 SQL Server 行版本号");
            }
            return version;
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("version 必须是有效的 Base64 文本");
        }
    }
}
