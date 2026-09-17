package cn.zqkj.platform.system.controller;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.domain.dto.CreateDictionaryItemCommand;
import cn.zqkj.platform.system.domain.dto.CreateDictionaryItemRequest;
import cn.zqkj.platform.system.domain.dto.CreateDictionaryTypeCommand;
import cn.zqkj.platform.system.domain.dto.CreateDictionaryTypeRequest;
import cn.zqkj.platform.system.domain.dto.UpdateDictionaryItemCommand;
import cn.zqkj.platform.system.domain.dto.UpdateDictionaryItemRequest;
import cn.zqkj.platform.system.domain.dto.UpdateDictionaryTypeCommand;
import cn.zqkj.platform.system.domain.dto.UpdateDictionaryTypeRequest;
import cn.zqkj.platform.system.domain.dto.UpsertParameterCommand;
import cn.zqkj.platform.system.domain.dto.UpsertParameterRequest;
import cn.zqkj.platform.system.domain.dto.CreateExternalEndpointRequest;
import cn.zqkj.platform.system.domain.dto.CreateExternalSystemRequest;
import cn.zqkj.platform.system.domain.dto.ExternalEndpointCommand;
import cn.zqkj.platform.system.domain.dto.ExternalSystemCommand;
import cn.zqkj.platform.system.domain.dto.UpdateExternalEndpointRequest;
import cn.zqkj.platform.system.domain.dto.UpdateExternalSystemRequest;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.domain.vo.DictionaryItemVO;
import cn.zqkj.platform.system.domain.vo.DictionaryTypeVO;
import cn.zqkj.platform.system.domain.vo.ParameterDefinitionVO;
import cn.zqkj.platform.system.domain.vo.ParameterValueVO;
import cn.zqkj.platform.system.domain.vo.ExternalEndpointVO;
import cn.zqkj.platform.system.domain.vo.ExternalSystemVO;
import cn.zqkj.platform.system.service.ConfigurationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
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

    /** @param service 平台配置服务 */
    public ConfigurationController(ConfigurationService service) {
        this.service = service;
    }

    /** @return 代码注册参数元数据 */
    @GetMapping("/parameter-definitions")
    @PreAuthorize("hasAuthority('configuration:read')")
    public ApiResponse<List<ParameterDefinitionVO>> parameterDefinitions() {
        return ApiResponse.success(service.findParameterDefinitions());
    }

    /** @param principal 当前主体 @return 当前机构范围内及全局参数值 */
    @GetMapping("/parameters")
    @PreAuthorize("hasAuthority('configuration:read')")
    public ApiResponse<List<ParameterValueVO>> parameters(
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.findParameterValues(actor(principal)));
    }

    /** @param key 注册参数键 @param request 写入请求 @param principal 当前主体 @return 写入后的安全值 */
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
            throw new InvalidRequestException("environment is invalid");
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

    /** @return 全部平台系统字典类型 */
    @GetMapping("/dictionaries")
    @PreAuthorize("hasAuthority('configuration:read')")
    public ApiResponse<List<DictionaryTypeVO>> dictionaries() {
        return ApiResponse.success(service.findDictionaryTypes());
    }

    /** @param request 创建请求 @param principal 当前主体 @return 新字典类型 */
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

    /** @param typeId 类型主键 @param request 修改请求 @param principal 当前主体 @return 修改后类型 */
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

    /** @param typeId 类型主键 @param includeDisabled 是否包含停用项 @return 字典项 */
    @GetMapping("/dictionaries/{typeId}/items")
    @PreAuthorize("hasAuthority('configuration:read')")
    public ApiResponse<List<DictionaryItemVO>> dictionaryItems(
            @PathVariable @Positive long typeId,
            @RequestParam(defaultValue = "false") boolean includeDisabled
    ) {
        return ApiResponse.success(service.findDictionaryItems(typeId, includeDisabled));
    }

    /** @param typeId 类型主键 @param request 创建请求 @param principal 当前主体 @return 新字典项 */
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

    /** @param itemId 字典项主键 @param request 修改请求 @param principal 当前主体 @return 修改后字典项 */
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

    /** @return 已确认外部系统 */
    @GetMapping("/external-systems")
    @PreAuthorize("hasAuthority('configuration:read')")
    public ApiResponse<List<ExternalSystemVO>> externalSystems() {
        return ApiResponse.success(service.findExternalSystems());
    }

    /** @param request 创建请求 @param principal 当前主体 @return 新外部系统 */
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

    /** @param systemId 系统主键 @param request 修改请求 @param principal 当前主体 @return 修改后系统 */
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

    /** @param systemId 系统主键 @param principal 当前主体 @return 授权范围内端点 */
    @GetMapping("/external-systems/{systemId}/endpoints")
    @PreAuthorize("hasAuthority('configuration:read')")
    public ApiResponse<List<ExternalEndpointVO>> externalEndpoints(
            @PathVariable @Positive long systemId,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.findExternalEndpoints(systemId, actor(principal)));
    }

    /** @param systemId 系统主键 @param request 创建请求 @param principal 当前主体 @return 新端点 */
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
                request.connectTimeoutMs(), request.readTimeoutMs(), request.credentialReference(), false, null
        ), actor(principal)));
    }

    /** @param endpointId 端点主键 @param request 修改请求 @param principal 当前主体 @return 修改后端点 */
    @PutMapping("/external-endpoints/{endpointId}")
    @PreAuthorize("hasAuthority('configuration:write')")
    public ApiResponse<ExternalEndpointVO> updateExternalEndpoint(
            @PathVariable @Positive long endpointId,
            @Valid @RequestBody UpdateExternalEndpointRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return ApiResponse.success(service.updateExternalEndpoint(endpointId, new ExternalEndpointCommand(
                null, null, request.baseUrl(), request.connectTimeoutMs(), request.readTimeoutMs(),
                request.credentialReference(), request.enabled(), decodeVersion(request.version())
        ), actor(principal)));
    }

    /** @param principal 当前主体 @return 服务端可信操作人上下文 */
    private AccessActor actor(PlatformUserPrincipal principal) {
        return new AccessActor(principal.userId(), principal.getUsername(), principal.organizationCodes());
    }

    /** @param value 环境文本 @return 环境枚举 */
    private ParameterEnvironment parseEnvironment(String value) {
        try {
            return ParameterEnvironment.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("environment is invalid");
        }
    }

    /** @param value 可选Base64版本 @return 解码值或空 */
    private byte[] decodeOptionalVersion(String value) {
        return value == null || value.isBlank() ? null : decodeVersion(value);
    }

    /** @param value Base64版本 @return 8字节SQL Server并发版本 */
    private byte[] decodeVersion(String value) {
        try {
            byte[] version = Base64.getDecoder().decode(value);
            if (version.length != Long.BYTES) {
                throw new IllegalArgumentException("invalid length");
            }
            return version;
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("version must be Base64 encoded 8-byte rowversion");
        }
    }
}
