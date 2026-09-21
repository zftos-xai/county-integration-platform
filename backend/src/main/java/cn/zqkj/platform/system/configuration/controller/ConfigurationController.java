package cn.zqkj.platform.system.configuration.controller;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.configuration.domain.dto.CreateDictionaryItemCommand;
import cn.zqkj.platform.system.configuration.domain.dto.CreateDictionaryItemRequest;
import cn.zqkj.platform.system.configuration.domain.dto.CreateDictionaryTypeCommand;
import cn.zqkj.platform.system.configuration.domain.dto.CreateDictionaryTypeRequest;
import cn.zqkj.platform.system.configuration.domain.dto.DeleteParameterCommand;
import cn.zqkj.platform.system.configuration.domain.dto.DeleteParameterRequest;
import cn.zqkj.platform.system.configuration.domain.dto.DeleteDictionaryRequest;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateDictionaryItemCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateDictionaryItemRequest;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateDictionaryTypeCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateDictionaryTypeRequest;
import cn.zqkj.platform.system.configuration.domain.dto.UpsertParameterCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpsertParameterRequest;
import cn.zqkj.platform.system.configuration.domain.dto.CreateExternalEndpointRequest;
import cn.zqkj.platform.system.configuration.domain.dto.CreateExternalSystemRequest;
import cn.zqkj.platform.system.configuration.domain.dto.ExternalEndpointCommand;
import cn.zqkj.platform.system.configuration.domain.dto.ExternalEndpointAuthenticationCommand;
import cn.zqkj.platform.system.configuration.domain.dto.ExternalEndpointAuthenticationRequest;
import cn.zqkj.platform.system.configuration.domain.dto.ExternalSystemCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateExternalEndpointRequest;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateExternalSystemRequest;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.configuration.domain.vo.DictionaryItemVO;
import cn.zqkj.platform.system.configuration.domain.vo.DictionaryTypeVO;
import cn.zqkj.platform.system.configuration.domain.vo.ParameterDefinitionVO;
import cn.zqkj.platform.system.configuration.domain.vo.ParameterValueVO;
import cn.zqkj.platform.system.configuration.domain.vo.ExternalEndpointVO;
import cn.zqkj.platform.system.configuration.domain.vo.ExternalEndpointAuthenticationVO;
import cn.zqkj.platform.system.configuration.domain.vo.ExternalSystemVO;
import cn.zqkj.platform.system.configuration.service.ConfigurationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

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
     * <p>需要 {@code configuration:read} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
     * <p>需要 {@code configuration:read} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param principal 当前用户
     * @return 当前机构范围内及全局参数值
     */
    @GetMapping("/parameters")
    @PreAuthorize("hasAuthority('configuration:read')")
    public ApiResponse<List<ParameterValueVO>> parameters(
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.findParameterValues(actor(principal)));
    }

    /**
     * 按参数键和适用范围新增或更新参数值。
     *
     * <p>需要 {@code configuration:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
            @Valid @RequestBody UpsertParameterRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        ParameterEnvironment environment;
        try {
            environment = ParameterEnvironment.valueOf(request.environment().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("environment 不是允许的环境名称");
        }
        return ApiResponse.success(service.upsertParameter(
                key,
                new UpsertParameterCommand(
                        environment, request.organizationId(), request.value(), request.enabled(),
                        decodeOptionalVersion(request.version())
                ),
                actor(principal)
        ));
    }

    /**
     * 按适用范围和并发版本删除参数值。
     *
     * <p>需要 {@code configuration:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
            @Valid @RequestBody DeleteParameterRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        service.deleteParameter(
                key,
                new DeleteParameterCommand(
                        parseEnvironment(request.environment()), request.organizationId(),
                        decodeVersion(request.version())
                ),
                actor(principal)
        );
        return ApiResponse.success(null);
    }

    /**
     * 查询系统字典类型。
     *
     * <p>需要 {@code configuration:read} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
     * <p>需要 {@code configuration:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param request 创建请求
     * @param principal 当前用户
     * @return 新字典类型
     */
    @PostMapping("/dictionaries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('configuration:write')")
    public ApiResponse<DictionaryTypeVO> createDictionaryType(
            @Valid @RequestBody CreateDictionaryTypeRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.createDictionaryType(
                new CreateDictionaryTypeCommand(request.typeCode(), request.typeName(), request.description()),
                actor(principal)
        ));
    }

    /**
     * 使用行版本更新系统字典类型。
     *
     * <p>需要 {@code configuration:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
            @Valid @RequestBody UpdateDictionaryTypeRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.updateDictionaryType(
                typeId,
                new UpdateDictionaryTypeCommand(
                        request.typeName(), request.description(), request.enabled(), decodeVersion(request.version())
                ),
                actor(principal)
        ));
    }

    /**
     * 删除未包含字典项的系统字典类型。
     *
     * <p>需要 {@code configuration:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
        service.deleteDictionaryType(typeId, decodeVersion(request.version()), actor(principal));
        return ApiResponse.success(null);
    }

    /**
     * 查询指定字典类型下的字典项。
     *
     * <p>需要 {@code configuration:read} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
     * <p>需要 {@code configuration:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
            @Valid @RequestBody CreateDictionaryItemRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.createDictionaryItem(
                typeId,
                new CreateDictionaryItemCommand(request.itemCode(), request.itemLabel(), request.sortOrder()),
                actor(principal)
        ));
    }

    /**
     * 使用行版本更新系统字典项。
     *
     * <p>需要 {@code configuration:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
            @Valid @RequestBody UpdateDictionaryItemRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.updateDictionaryItem(
                itemId,
                new UpdateDictionaryItemCommand(
                        request.itemLabel(), request.sortOrder(), request.enabled(), decodeVersion(request.version())
                ),
                actor(principal)
        ));
    }

    /**
     * 删除未被业务外键引用的系统字典项。
     *
     * <p>需要 {@code configuration:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
        service.deleteDictionaryItem(itemId, decodeVersion(request.version()), actor(principal));
        return ApiResponse.success(null);
    }

    /**
     * 查询已登记的外部系统。
     *
     * <p>需要 {@code configuration:read} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
     * <p>需要 {@code configuration:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
        return ApiResponse.success(service.createExternalSystem(new ExternalSystemCommand(
                request.systemCode(), request.systemName(), request.description(), true, null
        ), actor(principal)));
    }

    /**
     * 使用行版本更新外部系统登记信息。
     *
     * <p>需要 {@code configuration:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
        return ApiResponse.success(service.updateExternalSystem(systemId, new ExternalSystemCommand(
                null, request.systemName(), request.description(), request.enabled(), decodeVersion(request.version())
        ), actor(principal)));
    }

    /**
     * 查询指定外部系统的服务端点。
     *
     * <p>需要 {@code configuration:read} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
        return ApiResponse.success(service.findExternalEndpoints(systemId, actor(principal)));
    }

    /**
     * 读取指定端点的认证配置状态，不返回任何明文凭证。
     *
     * <p>需要 {@code configuration:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
                .body(ApiResponse.success(service.findExternalEndpointAuthentication(endpointId, actor(principal))));
    }

    /**
     * 创建尚未投入业务运行的外部系统端点。
     *
     * <p>需要 {@code configuration:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
        return ApiResponse.success(service.createExternalEndpoint(systemId, new ExternalEndpointCommand(
                parseEnvironment(request.environment()), request.organizationId(), request.baseUrl(),
                request.connectTimeoutMs(), request.readTimeoutMs(), authentication(request.authentication()),
                request.enabled(), null
        ), actor(principal)));
    }

    /**
     * 使用行版本更新端点并使旧验证结果失效。
     *
     * <p>需要 {@code configuration:write} 功能权限；资源范围和业务规则仍由服务层校验。</p>
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
        return ApiResponse.success(service.updateExternalEndpoint(endpointId, new ExternalEndpointCommand(
                parseEnvironment(request.environment()), request.organizationId(), request.baseUrl(),
                request.connectTimeoutMs(), request.readTimeoutMs(),
                authentication(request.authentication()), request.enabled(), decodeVersion(request.version())
        ), actor(principal)));
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
        return ApiResponse.success(service.verifyExternalEndpoint(endpointId, actor(principal)));
    }

    /**
     * 将API认证输入转换为服务层认证命令。
     *
     * @param request 可选认证请求
     * @return 服务层认证命令；未提交时为空
     */
    private ExternalEndpointAuthenticationCommand authentication(ExternalEndpointAuthenticationRequest request) {
        if (request == null) {
            return null;
        }
        return new ExternalEndpointAuthenticationCommand(
                request.vendorCode(), request.username(), request.password(), request.authorizationCode()
        );
    }

    /**
     * 将当前登录主体转换为携带机构范围的服务层操作人。
     *
     * @param principal 当前用户
     * @return 服务端可信操作人信息
     */
    private AccessActor actor(PlatformUserPrincipal principal) {
        return new AccessActor(principal.userId(), principal.getUsername(), principal.organizationCodes());
    }

    /**
     * 解析并校验外部端点运行环境代码。
     *
     * @param value 环境文本
     * @return 环境枚举
     */
    private ParameterEnvironment parseEnvironment(String value) {
        try {
            return ParameterEnvironment.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("environment 不是允许的环境名称");
        }
    }

    /**
     * 解码可选的SQL Server行版本；未提供时返回空值。
     *
     * @param value 可选Base64版本
     * @return 解码值或空
     */
    private byte[] decodeOptionalVersion(String value) {
        return value == null || value.isBlank() ? null : decodeVersion(value);
    }

    /**
     * 解码并校验客户端提交的SQL Server行版本。
     *
     * @param value Base64版本
     * @return 8字节SQL Server并发版本
     */
    private byte[] decodeVersion(String value) {
        try {
            byte[] version = Func.decodeBase64(value);
            if (version.length != Long.BYTES) {
                throw new IllegalArgumentException("invalid length");
            }
            return version;
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("version 必须是由 8 字节 SQL Server 行版本号编码得到的 Base64 文本");
        }
    }
}
