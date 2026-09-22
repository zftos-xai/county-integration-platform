package cn.zqkj.platform.system.configuration.controller;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.configuration.domain.dto.CreateDictionaryItemCommand;
import cn.zqkj.platform.system.configuration.domain.dto.CreateDictionaryTypeCommand;
import cn.zqkj.platform.system.configuration.domain.dto.CreateExternalEndpointRequest;
import cn.zqkj.platform.system.configuration.domain.dto.CreateExternalSystemRequest;
import cn.zqkj.platform.system.configuration.domain.dto.DeleteDictionaryRequest;
import cn.zqkj.platform.system.configuration.domain.dto.DeleteParameterCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateDictionaryItemCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateDictionaryTypeCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateExternalEndpointRequest;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateExternalSystemRequest;
import cn.zqkj.platform.system.configuration.domain.dto.UpsertParameterCommand;
import cn.zqkj.platform.system.configuration.domain.vo.DictionaryItemVO;
import cn.zqkj.platform.system.configuration.domain.vo.DictionaryTypeVO;
import cn.zqkj.platform.system.configuration.domain.vo.ExternalEndpointAuthenticationVO;
import cn.zqkj.platform.system.configuration.domain.vo.ExternalEndpointVO;
import cn.zqkj.platform.system.configuration.domain.vo.ExternalSystemVO;
import cn.zqkj.platform.system.configuration.domain.vo.ParameterDefinitionVO;
import cn.zqkj.platform.system.configuration.domain.vo.ParameterValueVO;
import cn.zqkj.platform.system.configuration.service.ConfigurationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供代码注册参数和平台系统字典管理API。
 */
@Validated
@RestController
@RequestMapping("/api/v1/configuration")
public class ConfigurationController {

    private final ConfigurationService service;

    /**
     * 创建平台参数、字典和外部系统配置控制器。
     *
     * @param service 平台配置服务
     */
    public ConfigurationController(ConfigurationService service) {
        this.service = service;
    }

    /**
     * 查询由后端代码注册的参数定义和校验边界。
     *
     * <p>需要 {@code configuration:read} 功能权限。</p>
     *
     * @return 代码注册参数元数据
     */
    @GetMapping("/parameter-definitions")
    @PreAuthorize("hasAuthority('configuration:read')")
    public ApiResponse<List<ParameterDefinitionVO>> parameterDefinitions() {
        return ApiResponse.success(service.findParameterDefinitions());
    }

    /**
     * 查询当前操作人可见的平台参数值。
     *
     * <p>需要 {@code configuration:read} 功能权限。</p>
     *
     * @param principal 当前用户
     * @return 当前机构范围内及全局参数值
     */
    @GetMapping("/parameters")
    @PreAuthorize("hasAuthority('configuration:read')")
    public ApiResponse<List<ParameterValueVO>> parameters(
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.findParameterValues(principal.accessActor()));
    }

    /**
     * 按参数键和适用范围新增或更新参数值。
     *
     * <p>需要 {@code configuration:write} 功能权限。</p>
     *
     * @param key 注册参数键
     * @param request 写入请求
     * @param principal 当前用户
     * @return 写入后的安全值
     */
    @PutMapping("/parameters/{key}")
    @PreAuthorize("hasAuthority('configuration:write')")
    public ApiResponse<ParameterValueVO> upsertParameter(
            @PathVariable String key,
            @Valid @RequestBody UpsertParameterCommand request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        key = Func.requireText(key, "parameterKey", 64).toLowerCase(Locale.ROOT);
        return ApiResponse.success(service.upsertParameter(
                key,
                request,
                principal.accessActor()
        ));
    }

    /**
     * 按适用范围和并发版本删除参数值。
     *
     * <p>需要 {@code configuration:write} 功能权限。</p>
     *
     * @param key 注册参数键
     * @param request 删除请求
     * @param principal 当前用户
     * @return 空成功响应
     */
    @DeleteMapping("/parameters/{key}")
    @PreAuthorize("hasAuthority('configuration:write')")
    public ApiResponse<Void> deleteParameter(
            @PathVariable String key,
            @Valid @RequestBody DeleteParameterCommand request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        key = Func.requireText(key, "parameterKey", 64).toLowerCase(Locale.ROOT);
        service.deleteParameter(
                key,
                request,
                principal.accessActor()
        );
        return ApiResponse.success(null);
    }

    /**
     * 查询系统字典类型。
     *
     * <p>需要 {@code configuration:read} 功能权限。</p>
     *
     * @return 全部平台系统字典类型
     */
    @GetMapping("/dictionaries")
    @PreAuthorize("hasAuthority('configuration:read')")
    public ApiResponse<List<DictionaryTypeVO>> dictionaries() {
        return ApiResponse.success(service.findDictionaryTypes());
    }

    /**
     * 创建系统字典类型。
     *
     * <p>需要 {@code configuration:write} 功能权限。</p>
     *
     * @param request 创建请求
     * @param principal 当前用户
     * @return 新字典类型
     */
    @PostMapping("/dictionaries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('configuration:write')")
    public ApiResponse<DictionaryTypeVO> createDictionaryType(
            @Valid @RequestBody CreateDictionaryTypeCommand request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.createDictionaryType(
                request,
                principal.accessActor()
        ));
    }

    /**
     * 使用行版本更新系统字典类型。
     *
     * <p>需要 {@code configuration:write} 功能权限。</p>
     *
     * @param typeId 类型主键
     * @param request 修改请求
     * @param principal 当前用户
     * @return 修改后类型
     */
    @PutMapping("/dictionaries/{typeId}")
    @PreAuthorize("hasAuthority('configuration:write')")
    public ApiResponse<DictionaryTypeVO> updateDictionaryType(
            @PathVariable @Positive long typeId,
            @Valid @RequestBody UpdateDictionaryTypeCommand request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.updateDictionaryType(
                typeId,
                request,
                principal.accessActor()
        ));
    }

    /**
     * 删除未包含字典项的系统字典类型。
     *
     * <p>需要 {@code configuration:write} 功能权限。</p>
     *
     * @param typeId 类型主键
     * @param request 删除请求
     * @param principal 当前用户
     * @return 空成功响应
     */
    @DeleteMapping("/dictionaries/{typeId}")
    @PreAuthorize("hasAuthority('configuration:write')")
    public ApiResponse<Void> deleteDictionaryType(
            @PathVariable @Positive long typeId,
            @Valid @RequestBody DeleteDictionaryRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        service.deleteDictionaryType(typeId, Func.decodeRowVersion(request.version()), principal.accessActor());
        return ApiResponse.success(null);
    }

    /**
     * 查询指定字典类型下的字典项。
     *
     * <p>需要 {@code configuration:read} 功能权限。</p>
     *
     * @param typeId 类型主键
     * @param includeDisabled 是否包含停用项
     * @return 字典项
     */
    @GetMapping("/dictionaries/{typeId}/items")
    @PreAuthorize("hasAuthority('configuration:read')")
    public ApiResponse<List<DictionaryItemVO>> dictionaryItems(
            @PathVariable @Positive long typeId,
            @RequestParam(defaultValue = "false") boolean includeDisabled
    ) {
        return ApiResponse.success(service.findDictionaryItems(typeId, includeDisabled));
    }

    /**
     * 在指定字典类型下创建字典项。
     *
     * <p>需要 {@code configuration:write} 功能权限。</p>
     *
     * @param typeId 类型主键
     * @param request 创建请求
     * @param principal 当前用户
     * @return 新字典项
     */
    @PostMapping("/dictionaries/{typeId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('configuration:write')")
    public ApiResponse<DictionaryItemVO> createDictionaryItem(
            @PathVariable @Positive long typeId,
            @Valid @RequestBody CreateDictionaryItemCommand request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.createDictionaryItem(
                typeId,
                request,
                principal.accessActor()
        ));
    }

    /**
     * 使用行版本更新系统字典项。
     *
     * <p>需要 {@code configuration:write} 功能权限。</p>
     *
     * @param itemId 字典项主键
     * @param request 修改请求
     * @param principal 当前用户
     * @return 修改后字典项
     */
    @PutMapping("/dictionary-items/{itemId}")
    @PreAuthorize("hasAuthority('configuration:write')")
    public ApiResponse<DictionaryItemVO> updateDictionaryItem(
            @PathVariable @Positive long itemId,
            @Valid @RequestBody UpdateDictionaryItemCommand request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.updateDictionaryItem(
                itemId,
                request,
                principal.accessActor()
        ));
    }

    /**
     * 删除未被业务外键引用的系统字典项。
     *
     * <p>需要 {@code configuration:write} 功能权限。</p>
     *
     * @param itemId 字典项主键
     * @param request 删除请求
     * @param principal 当前用户
     * @return 空成功响应
     */
    @DeleteMapping("/dictionary-items/{itemId}")
    @PreAuthorize("hasAuthority('configuration:write')")
    public ApiResponse<Void> deleteDictionaryItem(
            @PathVariable @Positive long itemId,
            @Valid @RequestBody DeleteDictionaryRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        service.deleteDictionaryItem(itemId, Func.decodeRowVersion(request.version()), principal.accessActor());
        return ApiResponse.success(null);
    }

    /**
     * 查询已登记的外部系统。
     *
     * <p>需要 {@code configuration:read} 功能权限。</p>
     *
     * @return 已确认外部系统
     */
    @GetMapping("/external-systems")
    @PreAuthorize("hasAuthority('configuration:read')")
    public ApiResponse<List<ExternalSystemVO>> externalSystems() {
        return ApiResponse.success(service.findExternalSystems());
    }

    /**
     * 创建外部系统登记信息。
     *
     * <p>需要 {@code configuration:write} 功能权限。</p>
     *
     * @param request 创建请求
     * @param principal 当前用户
     * @return 新外部系统
     */
    @PostMapping("/external-systems")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('configuration:write')")
    public ApiResponse<ExternalSystemVO> createExternalSystem(
            @Valid @RequestBody CreateExternalSystemRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.createExternalSystem(request, principal.accessActor()));
    }

    /**
     * 使用行版本更新外部系统登记信息。
     *
     * <p>需要 {@code configuration:write} 功能权限。</p>
     *
     * @param systemId 系统主键
     * @param request 修改请求
     * @param principal 当前用户
     * @return 修改后系统
     */
    @PutMapping("/external-systems/{systemId}")
    @PreAuthorize("hasAuthority('configuration:write')")
    public ApiResponse<ExternalSystemVO> updateExternalSystem(
            @PathVariable @Positive long systemId,
            @Valid @RequestBody UpdateExternalSystemRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.updateExternalSystem(systemId, request, principal.accessActor()));
    }

    /**
     * 查询指定外部系统的服务端点。
     *
     * <p>需要 {@code configuration:read} 功能权限。</p>
     *
     * @param systemId 系统主键
     * @param principal 当前用户
     * @return 授权范围内服务地址
     */
    @GetMapping("/external-systems/{systemId}/endpoints")
    @PreAuthorize("hasAuthority('configuration:read')")
    public ApiResponse<List<ExternalEndpointVO>> externalEndpoints(
            @PathVariable @Positive long systemId,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.findExternalEndpoints(systemId, principal.accessActor()));
    }

    /**
     * 供有配置写权限且获准访问该机构的管理员按需查看端点认证信息。
     *
     * <p>响应包含解密后的密码和授权码，仅用于编辑页短暂展示；禁止缓存、记录或复用为普通列表数据。</p>
     *
     * <p>需要 {@code configuration:write} 功能权限。</p>
     *
     * @param endpointId 服务地址主键
     * @param principal 当前用户
     * @return 禁止缓存的机构HIS接入信息
     */
    @GetMapping("/external-endpoints/{endpointId}/authentication")
    @PreAuthorize("hasAuthority('configuration:write')")
    public ResponseEntity<ApiResponse<ExternalEndpointAuthenticationVO>> externalEndpointAuthentication(
            @PathVariable @Positive long endpointId,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.findExternalEndpointAuthentication(endpointId, principal.accessActor())));
    }

    /**
     * 创建尚未投入业务运行的外部系统端点。
     *
     * <p>需要 {@code configuration:write} 功能权限。</p>
     *
     * @param systemId 系统主键
     * @param request 创建请求
     * @param principal 当前用户
     * @return 新服务地址
     */
    @PostMapping("/external-systems/{systemId}/endpoints")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('configuration:write')")
    public ApiResponse<ExternalEndpointVO> createExternalEndpoint(
            @PathVariable @Positive long systemId,
            @Valid @RequestBody CreateExternalEndpointRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.createExternalEndpoint(systemId, request, principal.accessActor()));
    }

    /**
     * 使用行版本更新端点并使旧验证结果失效。
     *
     * <p>需要 {@code configuration:write} 功能权限。</p>
     *
     * @param endpointId 服务地址主键
     * @param request 修改请求
     * @param principal 当前用户
     * @return 修改后服务地址
     */
    @PutMapping("/external-endpoints/{endpointId}")
    @PreAuthorize("hasAuthority('configuration:write')")
    public ApiResponse<ExternalEndpointVO> updateExternalEndpoint(
            @PathVariable @Positive long endpointId,
            @Valid @RequestBody UpdateExternalEndpointRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.updateExternalEndpoint(endpointId, request, principal.accessActor()));
    }

    /**
     * 以100-008确认当前保存的基层HIS配置只对应一个来源机构。
     *
     * @param endpointId 服务地址主键
     * @param principal 当前用户
     * @return 已写回自动校验结果的接口配置
     */
    @PostMapping("/external-endpoints/{endpointId}/verify")
    @PreAuthorize("hasAuthority('configuration:write')")
    public ApiResponse<ExternalEndpointVO> verifyExternalEndpoint(
            @PathVariable @Positive long endpointId,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.verifyExternalEndpoint(endpointId, principal.accessActor()));
    }


}
