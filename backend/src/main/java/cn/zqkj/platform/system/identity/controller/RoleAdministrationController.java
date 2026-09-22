package cn.zqkj.platform.system.identity.controller;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.identity.domain.dto.CreateRoleCommand;
import cn.zqkj.platform.system.identity.domain.dto.DeleteRoleRequest;
import cn.zqkj.platform.system.identity.domain.dto.ReplacePermissionCodesRequest;
import cn.zqkj.platform.system.identity.domain.dto.UpdateRoleCommand;
import cn.zqkj.platform.system.identity.domain.vo.PermissionVO;
import cn.zqkj.platform.system.identity.domain.vo.RoleVO;
import cn.zqkj.platform.system.identity.service.RoleAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供平台级角色和代码注册功能权限管理API。
 *
 * <p>功能权限在入口校验；角色和权限本身不是按机构分区的资源。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class RoleAdministrationController {

    private final RoleAdministrationService service;

    /**
     * 创建角色和权限管理控制器。
     *
     * @param service 角色管理服务
     */
    public RoleAdministrationController(RoleAdministrationService service) {
        this.service = service;
    }

    /**
     * 查询全部角色及其权限配置。
     *
     * <p>需要 {@code access:read} 功能权限。</p>
     *
     * @return 平台角色列表
     */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('access:read')")
    public ApiResponse<List<RoleVO>> findRoles() {
        return ApiResponse.success(service.findAll());
    }

    /**
     * 按主键读取角色及其权限配置。
     *
     * <p>需要 {@code access:read} 功能权限。</p>
     *
     * @param roleId 角色主键
     * @return 角色详情
     */
    @GetMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('access:read')")
    public ApiResponse<RoleVO> getRole(@PathVariable @Positive long roleId) {
        return ApiResponse.success(service.get(roleId));
    }

    /**
     * 创建角色。
     *
     * <p>需要 {@code access:write} 功能权限。</p>
     *
     * @param request 创建请求
     * @param principal 当前用户
     * @return 新角色
     */
    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('access:write')")
    public ApiResponse<RoleVO> createRole(
            @Valid @RequestBody CreateRoleCommand request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.create(
                request, principal.accessActor()
        ));
    }

    /**
     * 按并发版本更新角色资料。
     *
     * <p>需要 {@code access:write} 功能权限。</p>
     *
     * @param roleId 角色主键
     * @param request 修改请求
     * @param principal 当前用户
     * @return 修改后角色
     */
    @PutMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('access:write')")
    public ApiResponse<RoleVO> updateRole(
            @PathVariable @Positive long roleId,
            @Valid @RequestBody UpdateRoleCommand request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.update(roleId, request, principal.accessActor()));
    }

    /**
     * 删除未被用户引用且不受平台保护的角色。
     *
     * <p>需要 {@code access:write} 功能权限。</p>
     *
     * @param roleId 角色主键
     * @param request 删除请求
     * @param principal 当前用户
     * @return 空成功响应
     */
    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('access:write')")
    public ApiResponse<Void> deleteRole(
            @PathVariable @Positive long roleId,
            @Valid @RequestBody DeleteRoleRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        service.delete(roleId, Func.decodeRowVersion(request.version()), principal.accessActor());
        return ApiResponse.success(null);
    }

    /**
     * 整体替换角色的功能权限集合。
     *
     * <p>需要 {@code access:write} 功能权限。</p>
     *
     * @param roleId 角色主键
     * @param request 权限请求
     * @param principal 当前用户
     * @return 修改后角色
     */
    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('access:write')")
    public ApiResponse<RoleVO> replacePermissions(
            @PathVariable @Positive long roleId,
            @Valid @RequestBody ReplacePermissionCodesRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.replacePermissions(
                roleId, request.permissionCodes(), principal.accessActor()
        ));
    }

    /**
     * 查询后端已注册的功能权限。
     *
     * <p>需要 {@code access:read} 功能权限。</p>
     *
     * @return 后端代码注册权限清单
     */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('access:read')")
    public ApiResponse<List<PermissionVO>> findPermissions() {
        return ApiResponse.success(service.findPermissions());
    }

}
