package cn.zqkj.platform.system.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.exception.ResourceNotFoundException;
import cn.zqkj.platform.system.domain.dto.CreateDictionaryItemCommand;
import cn.zqkj.platform.system.domain.dto.CreateDictionaryTypeCommand;
import cn.zqkj.platform.system.domain.dto.DeleteParameterCommand;
import cn.zqkj.platform.system.domain.dto.UpdateDictionaryItemCommand;
import cn.zqkj.platform.system.domain.dto.UpdateDictionaryTypeCommand;
import cn.zqkj.platform.system.domain.dto.UpsertParameterCommand;
import cn.zqkj.platform.system.domain.dto.ExternalEndpointCommand;
import cn.zqkj.platform.system.domain.dto.ExternalSystemCommand;
import cn.zqkj.platform.system.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.model.DictionaryItem;
import cn.zqkj.platform.system.domain.model.DictionaryType;
import cn.zqkj.platform.system.domain.model.ParameterDefinition;
import cn.zqkj.platform.system.domain.model.ParameterValue;
import cn.zqkj.platform.system.domain.model.ParameterValueType;
import cn.zqkj.platform.system.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.domain.model.ExternalSystem;
import cn.zqkj.platform.system.domain.vo.DictionaryItemVO;
import cn.zqkj.platform.system.domain.vo.DictionaryTypeVO;
import cn.zqkj.platform.system.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.domain.vo.ParameterDefinitionVO;
import cn.zqkj.platform.system.domain.vo.ParameterValueVO;
import cn.zqkj.platform.system.domain.vo.ExternalEndpointVO;
import cn.zqkj.platform.system.domain.vo.ExternalSystemVO;
import cn.zqkj.platform.system.mapper.ConfigurationMapper;
import cn.zqkj.platform.system.service.ConfigurationService;
import cn.zqkj.platform.system.service.OrganizationService;
import cn.zqkj.platform.system.service.ParameterDefinitionRegistry;
import cn.zqkj.platform.system.service.ManagementAuditService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 实现代码注册参数、适用范围校验和平台系统字典管理规则。
 */
@Service
public class ConfigurationServiceImpl implements ConfigurationService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_.-]{0,63}");
    private static final Pattern PARAMETER_KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");
    private static final String MASKED_VALUE = "******";

    private final ConfigurationMapper mapper;
    private final ParameterDefinitionRegistry registry;
    private final OrganizationService organizationService;
    private final ManagementAuditService auditService;

    /**
     * 创建平台配置服务。
     *
     * @param mapper 配置Mapper
     * @param registry 代码注册参数清单
     * @param organizationService 机构查询服务
     * @param auditService 管理审计服务
     */
    public ConfigurationServiceImpl(
            ConfigurationMapper mapper,
            ParameterDefinitionRegistry registry,
            OrganizationService organizationService,
            ManagementAuditService auditService
    ) {
        this.mapper = mapper;
        this.registry = registry;
        this.organizationService = organizationService;
        this.auditService = auditService;
    }

    /** {@inheritDoc} */
    @Transactional(readOnly = true)
    @Override
    public List<ParameterDefinitionVO> findParameterDefinitions() {
        return registry.findAll().stream().map(this::toDefinitionVO).toList();
    }

    /** {@inheritDoc} */
    @Transactional(readOnly = true)
    @Override
    public List<ParameterValueVO> findParameterValues(AccessActor actor) {
        return mapper.findParameterValues().stream()
                .filter(value -> value.organizationCode() == null || actor.canAccess(value.organizationCode()))
                .map(value -> toParameterValueVO(value, requireDefinition(value.parameterKey())))
                .toList();
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public ParameterValueVO upsertParameter(String key, UpsertParameterCommand command, AccessActor actor) {
        ParameterDefinition definition = requireDefinition(normalizeParameterKey(key));
        validateParameterScope(definition, command, actor);
        String normalizedValue = validateAndNormalizeValue(definition, command.value());
        UpsertParameterCommand normalized = new UpsertParameterCommand(
                command.environment(), command.organizationId(), normalizedValue, command.enabled(),
                command.expectedVersion()
        );
        ParameterValue current = mapper.findParameterValue(definition.key(), normalized).orElse(null);
        long id;
        if (current == null) {
            if (normalized.expectedVersion() != null) {
                throw new ResourceConflictException("参数值尚未创建，请刷新后重试");
            }
            id = mapper.createParameterValue(
                    definition.key(), definition.valueType().name(), normalized, actor.loginName()
            );
        } else {
            requireVersion(normalized.expectedVersion());
            if (mapper.updateParameterValue(
                    current.id(), definition.valueType().name(), normalized, actor.loginName()
            ) != 1) {
                throw new ResourceConflictException("参数值已被他人修改，请刷新后重试");
            }
            id = current.id();
        }
        ParameterValue saved = mapper.findParameterValueById(id)
                .orElseThrow(() -> new IllegalStateException("Saved parameter value was not found"));
        if (!saved.parameterKey().equals(definition.key())
                || saved.environment() != normalized.environment()
                || !java.util.Objects.equals(saved.organizationId(), normalized.organizationId())) {
            throw new IllegalStateException("保存参数后读取到了不符合环境和机构条件的记录");
        }
        ParameterValueVO result = toParameterValueVO(saved, definition);
        audit(actor, result.organizationId(), result.organizationCode(),
                current == null ? "PARAMETER_CREATED" : "PARAMETER_UPDATED", "PARAMETER", definition.key(),
                "已保存参数适用范围、环境和启用状态；参数值未写入审计摘要");
        return result;
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public void deleteParameter(String key, DeleteParameterCommand command, AccessActor actor) {
        ParameterDefinition definition = requireDefinition(normalizeParameterKey(key));
        if (command == null) {
            throw new InvalidRequestException("必须提供要删除的参数适用范围");
        }
        UpsertParameterCommand scope = new UpsertParameterCommand(
                command.environment(), command.organizationId(), "", false, command.expectedVersion()
        );
        validateParameterScope(definition, scope, actor);
        requireVersion(command.expectedVersion());
        ParameterValue current = mapper.findParameterValue(definition.key(), scope)
                .orElseThrow(() -> new ResourceNotFoundException("要删除的参数配置不存在"));
        if (mapper.deleteParameterValue(current.id(), command.expectedVersion()) != 1) {
            throw new ResourceConflictException("参数配置已被他人修改，请刷新后重试");
        }
        audit(actor, current.organizationId(), current.organizationCode(),
                "PARAMETER_DELETED", "PARAMETER", definition.key(),
                "已删除指定环境和机构范围的参数配置；参数值未写入审计摘要");
    }

    /** {@inheritDoc} */
    @Transactional(readOnly = true)
    @Override
    public List<DictionaryTypeVO> findDictionaryTypes() {
        return mapper.findDictionaryTypes().stream().map(this::toDictionaryTypeVO).toList();
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public DictionaryTypeVO createDictionaryType(CreateDictionaryTypeCommand command, AccessActor actor) {
        String typeCode = normalizeCode(command.typeCode(), "typeCode");
        if (mapper.dictionaryTypeCodeExists(typeCode)) {
            throw new ResourceConflictException("字典类型代码已存在");
        }
        CreateDictionaryTypeCommand normalized = new CreateDictionaryTypeCommand(
                typeCode,
                requireText(command.typeName(), "typeName", 100),
                requireText(command.description(), "description", 500)
        );
        DictionaryTypeVO result = toDictionaryTypeVO(requireDictionaryType(
                mapper.createDictionaryType(normalized, requireActor(actor.loginName()))
        ));
        audit(actor, null, null, "DICTIONARY_TYPE_CREATED", "DICTIONARY_TYPE", result.typeCode(),
                "已创建平台系统字典类型");
        return result;
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public DictionaryTypeVO updateDictionaryType(
            long typeId,
            UpdateDictionaryTypeCommand command,
            AccessActor actor
    ) {
        requireDictionaryType(typeId);
        requireVersion(command.expectedVersion());
        UpdateDictionaryTypeCommand normalized = new UpdateDictionaryTypeCommand(
                requireText(command.typeName(), "typeName", 100),
                requireText(command.description(), "description", 500),
                command.enabled(),
                command.expectedVersion()
        );
        if (mapper.updateDictionaryType(typeId, normalized, requireActor(actor.loginName())) != 1) {
            throw new ResourceConflictException("字典类型已被他人修改，请刷新后重试");
        }
        DictionaryTypeVO result = toDictionaryTypeVO(requireDictionaryType(typeId));
        audit(actor, null, null, "DICTIONARY_TYPE_UPDATED", "DICTIONARY_TYPE", result.typeCode(),
                "已修改字典类型展示信息和启用状态");
        return result;
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public void deleteDictionaryType(long typeId, byte[] expectedVersion, AccessActor actor) {
        DictionaryType current = requireDictionaryType(typeId);
        requireVersion(expectedVersion);
        long itemCount = mapper.countDictionaryItems(typeId);
        if (itemCount > 0) {
            throw new ResourceConflictException(
                    "该字典类型仍包含" + itemCount + "个字典项，请先逐项确认使用情况并删除字典项"
            );
        }
        if (mapper.deleteDictionaryType(typeId, expectedVersion) != 1) {
            throw new ResourceConflictException("字典类型已被他人修改或新增了字典项，请刷新后重试");
        }
        audit(actor, null, null, "DICTIONARY_TYPE_DELETED", "DICTIONARY_TYPE", current.typeCode(),
                "已删除不含字典项的字典类型");
    }

    /** {@inheritDoc} */
    @Transactional(readOnly = true)
    @Override
    public List<DictionaryItemVO> findDictionaryItems(long typeId, boolean includeDisabled) {
        requireDictionaryType(typeId);
        return mapper.findDictionaryItems(typeId, includeDisabled).stream().map(this::toDictionaryItemVO).toList();
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public DictionaryItemVO createDictionaryItem(
            long typeId,
            CreateDictionaryItemCommand command,
            AccessActor actor
    ) {
        DictionaryType type = requireDictionaryType(typeId);
        if (!type.enabled()) {
            throw new ResourceConflictException("已停用的字典类型不能新增字典项");
        }
        String itemCode = normalizeCode(command.itemCode(), "itemCode");
        if (mapper.dictionaryItemCodeExists(typeId, itemCode)) {
            throw new ResourceConflictException("该字典类型中已存在相同的字典项代码");
        }
        validateSortOrder(command.sortOrder());
        CreateDictionaryItemCommand normalized = new CreateDictionaryItemCommand(
                itemCode, requireText(command.itemLabel(), "itemLabel", 200), command.sortOrder()
        );
        DictionaryItemVO result = toDictionaryItemVO(requireDictionaryItem(
                mapper.createDictionaryItem(typeId, normalized, requireActor(actor.loginName()))
        ));
        audit(actor, null, null, "DICTIONARY_ITEM_CREATED", "DICTIONARY_ITEM",
                type.typeCode() + ":" + result.itemCode(), "已创建系统字典项");
        return result;
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public DictionaryItemVO updateDictionaryItem(
            long itemId,
            UpdateDictionaryItemCommand command,
            AccessActor actor
    ) {
        DictionaryItem current = requireDictionaryItem(itemId);
        DictionaryType type = requireDictionaryType(current.dictionaryTypeId());
        requireVersion(command.expectedVersion());
        validateSortOrder(command.sortOrder());
        UpdateDictionaryItemCommand normalized = new UpdateDictionaryItemCommand(
                requireText(command.itemLabel(), "itemLabel", 200),
                command.sortOrder(), command.enabled(), command.expectedVersion()
        );
        if (mapper.updateDictionaryItem(itemId, normalized, requireActor(actor.loginName())) != 1) {
            throw new ResourceConflictException("字典项已被他人修改，请刷新后重试");
        }
        DictionaryItemVO result = toDictionaryItemVO(requireDictionaryItem(itemId));
        audit(actor, null, null, "DICTIONARY_ITEM_UPDATED", "DICTIONARY_ITEM",
                type.typeCode() + ":" + result.itemCode(),
                "已修改字典项文本、顺序和启用状态");
        return result;
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public void deleteDictionaryItem(long itemId, byte[] expectedVersion, AccessActor actor) {
        DictionaryItem current = requireDictionaryItem(itemId);
        DictionaryType type = requireDictionaryType(current.dictionaryTypeId());
        requireVersion(expectedVersion);
        long referenceCount = mapper.countDictionaryItemReferences(itemId);
        if (referenceCount > 0) {
            throw new ResourceConflictException(
                    "该字典项已被" + referenceCount + "条业务数据使用，不能删除；可以停用，历史数据仍保留原含义"
            );
        }
        if (mapper.deleteDictionaryItem(itemId, expectedVersion) != 1) {
            throw new ResourceConflictException("字典项已被他人修改或开始被业务使用，请刷新后重试");
        }
        audit(actor, null, null, "DICTIONARY_ITEM_DELETED", "DICTIONARY_ITEM",
                type.typeCode() + ":" + current.itemCode(), "已删除未被业务数据使用的字典项");
    }

    /** {@inheritDoc} */
    @Transactional(readOnly = true)
    @Override
    public List<ExternalSystemVO> findExternalSystems() {
        return mapper.findExternalSystems().stream().map(this::toExternalSystemVO).toList();
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public ExternalSystemVO createExternalSystem(ExternalSystemCommand command, AccessActor actor) {
        String code = normalizeCode(command.systemCode(), "systemCode");
        if (mapper.externalSystemCodeExists(code)) {
            throw new ResourceConflictException("外部系统代码已存在");
        }
        ExternalSystemCommand normalized = new ExternalSystemCommand(
                code, requireText(command.systemName(), "systemName", 100),
                requireText(command.description(), "description", 500), true, null
        );
        ExternalSystemVO result = toExternalSystemVO(requireExternalSystem(
                mapper.createExternalSystem(normalized, requireActor(actor.loginName()))
        ));
        audit(actor, null, null, "EXTERNAL_SYSTEM_CREATED", "EXTERNAL_SYSTEM", result.systemCode(),
                "已创建外部系统身份；未记录连接地址或凭证引用");
        return result;
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public ExternalSystemVO updateExternalSystem(long systemId, ExternalSystemCommand command, AccessActor actor) {
        requireExternalSystem(systemId);
        requireVersion(command.expectedVersion());
        ExternalSystemCommand normalized = new ExternalSystemCommand(
                null, requireText(command.systemName(), "systemName", 100),
                requireText(command.description(), "description", 500), command.enabled(), command.expectedVersion()
        );
        if (mapper.updateExternalSystem(systemId, normalized, requireActor(actor.loginName())) != 1) {
            throw new ResourceConflictException("外部系统资料已被他人修改，请刷新后重试");
        }
        ExternalSystemVO result = toExternalSystemVO(requireExternalSystem(systemId));
        audit(actor, null, null, "EXTERNAL_SYSTEM_UPDATED", "EXTERNAL_SYSTEM", result.systemCode(),
                "已修改外部系统展示信息和启用状态");
        return result;
    }

    /** {@inheritDoc} */
    @Transactional(readOnly = true)
    @Override
    public List<ExternalEndpointVO> findExternalEndpoints(long systemId, AccessActor actor) {
        requireExternalSystem(systemId);
        return mapper.findExternalEndpoints(systemId).stream()
                .filter(endpoint -> endpoint.organizationCode() == null
                        || actor.canAccess(endpoint.organizationCode()))
                .map(this::toExternalEndpointVO)
                .toList();
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public ExternalEndpointVO createExternalEndpoint(
            long systemId,
            ExternalEndpointCommand command,
            AccessActor actor
    ) {
        ExternalSystem system = requireExternalSystem(systemId);
        if (!system.enabled()) {
            throw new ResourceConflictException("外部系统已停用，不能新增服务地址");
        }
        validateEndpointScope(command, actor);
        ExternalEndpointCommand normalized = normalizeEndpoint(command, false, null);
        if (mapper.externalEndpointScopeExists(systemId, normalized)) {
            throw new ResourceConflictException("该外部系统、环境和机构已经配置了服务地址");
        }
        long id = mapper.createExternalEndpoint(systemId, normalized, requireActor(actor.loginName()));
        ExternalEndpointVO result = toExternalEndpointVO(requireExternalEndpoint(id));
        audit(actor, result.organizationId(), result.organizationCode(), "EXTERNAL_ENDPOINT_CREATED",
                "EXTERNAL_ENDPOINT", String.valueOf(id),
                "已创建停用服务地址；地址和凭证引用未写入审计摘要");
        return result;
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public ExternalEndpointVO updateExternalEndpoint(
            long endpointId,
            ExternalEndpointCommand command,
            AccessActor actor
    ) {
        ExternalEndpoint current = requireExternalEndpoint(endpointId);
        requireVersion(command.expectedVersion());
        requireEndpointAccess(current, actor);
        ExternalEndpointCommand normalized = normalizeEndpoint(
                new ExternalEndpointCommand(current.environment(), current.organizationId(), command.baseUrl(),
                        command.connectTimeoutMs(), command.readTimeoutMs(), command.credentialReference(),
                        command.enabled(), command.expectedVersion()),
                command.enabled(), command.expectedVersion()
        );
        if (normalized.enabled() && !requireExternalSystem(current.externalSystemId()).enabled()) {
            throw new ResourceConflictException("外部系统已停用，不能启用其服务地址");
        }
        if (mapper.updateExternalEndpoint(endpointId, normalized, requireActor(actor.loginName())) != 1) {
            throw new ResourceConflictException("服务地址资料已被他人修改，请刷新后重试");
        }
        ExternalEndpointVO result = toExternalEndpointVO(requireExternalEndpoint(endpointId));
        audit(actor, result.organizationId(), result.organizationCode(), "EXTERNAL_ENDPOINT_UPDATED",
                "EXTERNAL_ENDPOINT", String.valueOf(endpointId),
                "已修改服务地址超时和启用状态；地址和凭证引用未写入审计摘要");
        return result;
    }

    /** @param definition 参数定义 @return API元数据 */
    private ParameterDefinitionVO toDefinitionVO(ParameterDefinition definition) {
        return new ParameterDefinitionVO(
                definition.key(), definition.name(), definition.valueType().name(),
                definition.environments().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                definition.organizationScoped(), definition.sensitive(), definition.maximumLength(),
                definition.minimumNumber(), definition.maximumNumber(), definition.pattern()
        );
    }

    /** @param value 参数值 @param definition 参数定义 @return 安全输出 */
    private ParameterValueVO toParameterValueVO(ParameterValue value, ParameterDefinition definition) {
        return new ParameterValueVO(
                value.id(), value.parameterKey(), value.valueType().name(), value.environment().name(),
                value.organizationId(), value.organizationCode(),
                definition.sensitive() ? MASKED_VALUE : value.value(), true, value.enabled(), value.updatedAt(),
                Base64.getEncoder().encodeToString(value.version())
        );
    }

    /** @param type 字典类型 @return API输出 */
    private DictionaryTypeVO toDictionaryTypeVO(DictionaryType type) {
        return new DictionaryTypeVO(
                type.id(), type.typeCode(), type.typeName(), type.description(), type.enabled(),
                type.createdAt(), type.updatedAt(), Base64.getEncoder().encodeToString(type.version())
        );
    }

    /** @param item 字典项 @return API输出 */
    private DictionaryItemVO toDictionaryItemVO(DictionaryItem item) {
        return new DictionaryItemVO(
                item.id(), item.dictionaryTypeId(), item.itemCode(), item.itemLabel(), item.sortOrder(),
                item.enabled(), item.createdAt(), item.updatedAt(), Base64.getEncoder().encodeToString(item.version())
        );
    }

    /** @param system 外部系统 @return API输出 */
    private ExternalSystemVO toExternalSystemVO(ExternalSystem system) {
        return new ExternalSystemVO(system.id(), system.systemCode(), system.systemName(), system.description(),
                system.enabled(), system.createdAt(), system.updatedAt(),
                Base64.getEncoder().encodeToString(system.version()));
    }

    /** @param endpoint 外部服务地址 @return 不含凭证引用的API输出 */
    private ExternalEndpointVO toExternalEndpointVO(ExternalEndpoint endpoint) {
        return new ExternalEndpointVO(endpoint.id(), endpoint.externalSystemId(), endpoint.environment().name(),
                endpoint.organizationId(), endpoint.organizationCode(), endpoint.baseUrl(),
                endpoint.connectTimeoutMs(), endpoint.readTimeoutMs(),
                endpoint.credentialReference() != null && !endpoint.credentialReference().isBlank(),
                endpoint.enabled(), endpoint.createdAt(), endpoint.updatedAt(),
                Base64.getEncoder().encodeToString(endpoint.version()));
    }

    /** @param key 参数键 @return 已注册定义 */
    private ParameterDefinition requireDefinition(String key) {
        return registry.find(key)
                .orElseThrow(() -> new InvalidRequestException("Parameter key is not registered"));
    }

    /** @param definition 参数定义 @param command 写入命令 @param actor 操作人 */
    private void validateParameterScope(
            ParameterDefinition definition,
            UpsertParameterCommand command,
            AccessActor actor
    ) {
        if (command == null || command.environment() == null
                || !definition.environments().contains(command.environment())) {
            throw new InvalidRequestException("参数不能用于所选环境");
        }
        if (definition.organizationScoped()) {
            if (command.organizationId() == null || command.organizationId() <= 0) {
                throw new InvalidRequestException("机构专用参数必须提供 organizationId");
            }
            OrganizationVO organization = organizationService.get(command.organizationId());
            if (!actor.canAccess(organization.organizationCode())) {
                throw new AccessDeniedException("当前账号无权访问该机构");
            }
        } else if (command.organizationId() != null) {
            throw new InvalidRequestException("全局参数不能包含 organizationId");
        }
    }

    /** @param definition 参数定义 @param rawValue 原始值 @return 规范化值 */
    private String validateAndNormalizeValue(ParameterDefinition definition, String rawValue) {
        if (rawValue == null) {
            throw new InvalidRequestException("必须提供参数值");
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            throw new InvalidRequestException("参数值不能为空");
        }
        if (definition.valueType() == ParameterValueType.STRING) {
            if (definition.maximumLength() != null && value.length() > definition.maximumLength()) {
                throw new InvalidRequestException("参数值超过允许的最大长度");
            }
            validatePattern(definition.pattern(), value);
            return value;
        }
        if (definition.valueType() == ParameterValueType.BOOLEAN) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new InvalidRequestException("参数值必须是 true 或 false");
            }
            return value.toLowerCase(Locale.ROOT);
        }
        try {
            BigDecimal number = new BigDecimal(value);
            if (definition.valueType() == ParameterValueType.INTEGER && number.stripTrailingZeros().scale() > 0) {
                throw new InvalidRequestException("参数值必须是整数");
            }
            if (definition.minimumNumber() != null && number.compareTo(definition.minimumNumber()) < 0
                    || definition.maximumNumber() != null && number.compareTo(definition.maximumNumber()) > 0) {
                throw new InvalidRequestException("参数值超出登记的允许范围");
            }
            return number.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException exception) {
            throw new InvalidRequestException("参数值的数字类型不正确");
        }
    }

    /** @param expression 可选正则 @param value 文本值 */
    private void validatePattern(String expression, String value) {
        if (expression == null) {
            return;
        }
        try {
            if (!Pattern.matches(expression, value)) {
                throw new InvalidRequestException("参数值不符合登记的格式");
            }
        } catch (PatternSyntaxException exception) {
            throw new IllegalStateException("代码中登记的参数格式规则无效", exception);
        }
    }

    /** @param typeId 类型主键 @return 存在的字典类型 */
    private DictionaryType requireDictionaryType(long typeId) {
        if (typeId <= 0) {
            throw new InvalidRequestException("dictionaryTypeId 必须大于 0");
        }
        return mapper.findDictionaryType(typeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary type was not found"));
    }

    /** @param itemId 字典项主键 @return 存在的字典项 */
    private DictionaryItem requireDictionaryItem(long itemId) {
        if (itemId <= 0) {
            throw new InvalidRequestException("dictionaryItemId 必须大于 0");
        }
        return mapper.findDictionaryItem(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary item was not found"));
    }

    /** @param systemId 外部系统主键 @return 已存在系统 */
    private ExternalSystem requireExternalSystem(long systemId) {
        if (systemId <= 0) {
            throw new InvalidRequestException("externalSystemId 必须大于 0");
        }
        return mapper.findExternalSystem(systemId)
                .orElseThrow(() -> new ResourceNotFoundException("External system was not found"));
    }

    /** @param endpointId 外部服务地址主键 @return 已存在服务地址 */
    private ExternalEndpoint requireExternalEndpoint(long endpointId) {
        if (endpointId <= 0) {
            throw new InvalidRequestException("externalEndpointId 必须大于 0");
        }
        return mapper.findExternalEndpoint(endpointId)
                .orElseThrow(() -> new ResourceNotFoundException("External endpoint was not found"));
    }

    /** @param command 服务地址命令 @param actor 操作人 */
    private void validateEndpointScope(ExternalEndpointCommand command, AccessActor actor) {
        if (command == null || command.environment() == null) {
            throw new InvalidRequestException("必须选择外部系统服务地址的使用环境");
        }
        if (command.organizationId() != null) {
            OrganizationVO organization = organizationService.get(command.organizationId());
            if (!actor.canAccess(organization.organizationCode())) {
                throw new AccessDeniedException("当前账号无权访问该机构");
            }
        }
    }

    /** @param endpoint 服务地址 @param actor 操作人 */
    private void requireEndpointAccess(ExternalEndpoint endpoint, AccessActor actor) {
        if (endpoint.organizationCode() != null && !actor.canAccess(endpoint.organizationCode())) {
            throw new AccessDeniedException("当前账号无权访问该机构");
        }
    }

    /** @param command 原命令 @param enabled 是否启用 @param version 并发版本 @return 规范化命令 */
    private ExternalEndpointCommand normalizeEndpoint(
            ExternalEndpointCommand command,
            boolean enabled,
            byte[] version
    ) {
        String baseUrl = requireHttpsUrl(command.baseUrl());
        if (command.connectTimeoutMs() < 100 || command.connectTimeoutMs() > 60000) {
            throw new InvalidRequestException("connectTimeoutMs 超出允许范围");
        }
        if (command.readTimeoutMs() < command.connectTimeoutMs() || command.readTimeoutMs() > 300000) {
            throw new InvalidRequestException("readTimeoutMs 超出允许范围");
        }
        String reference = requireCredentialReference(command.credentialReference());
        return new ExternalEndpointCommand(command.environment(), command.organizationId(), baseUrl,
                command.connectTimeoutMs(), command.readTimeoutMs(), reference, enabled, version);
    }

    /** @param actor 操作人 @param organizationId 机构主键 @param organizationCode 机构代码 @param action 动作 @param targetType 目标类型 @param targetId 目标标识 @param summary 不含敏感内容的摘要 */
    private void audit(AccessActor actor, Long organizationId, String organizationCode, String action,
                       String targetType, String targetId, String summary) {
        auditService.recordSuccess(new ManagementAuditCommand(actor, null, organizationId, organizationCode,
                action, targetType, targetId, "SUCCESS", summary,
                ManagementAuditServiceImpl.currentRequestId()));
    }

    /** @param value 地址 @return 规范化HTTPS地址 */
    private String requireHttpsUrl(String value) {
        String normalized = requireText(value, "baseUrl", 500);
        try {
            URI uri = new URI(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new InvalidRequestException("baseUrl 必须是 HTTPS 基础地址，且不能包含账号、密码或查询参数");
            }
            return uri.normalize().toASCIIString();
        } catch (URISyntaxException exception) {
            throw new InvalidRequestException("baseUrl 格式无效");
        }
    }

    /** @param value 凭证引用 @return 规范化不透明引用 */
    private String requireCredentialReference(String value) {
        String reference = requireText(value, "credentialReference", 200);
        if (!reference.matches("[a-z][a-z0-9+.-]*://\\S+")) {
            throw new InvalidRequestException("credentialReference 必须使用获准的凭证引用地址");
        }
        return reference;
    }

    /** @param value 代码 @param field 字段名 @return 规范化代码 */
    private String normalizeCode(String value, String field) {
        String code = requireText(value, field, 64).toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new InvalidRequestException(field + " has an invalid format");
        }
        return code;
    }

    /** @param value 参数键 @return 规范化的小写参数键 */
    private String normalizeParameterKey(String value) {
        String key = requireText(value, "parameterKey", 64).toLowerCase(Locale.ROOT);
        if (!PARAMETER_KEY_PATTERN.matcher(key).matches()) {
            throw new InvalidRequestException("parameterKey 格式无效");
        }
        return key;
    }

    /** @param value 文本 @param field 字段名 @param maximumLength 最大长度 @return 裁剪值 */
    private String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.trim().length() > maximumLength) {
            throw new InvalidRequestException(field + " is invalid");
        }
        return value.trim();
    }

    /** @param actor 操作人 @return 裁剪后的操作人 */
    private String requireActor(String actor) {
        return requireText(actor, "actor", 64);
    }

    /** @param version SQL Server并发版本 */
    private void requireVersion(byte[] version) {
        if (version == null || version.length != Long.BYTES) {
            throw new InvalidRequestException("version 必须是 8 字节的 SQL Server 行版本号");
        }
    }

    /** @param sortOrder 展示顺序 */
    private void validateSortOrder(int sortOrder) {
        if (sortOrder < 0 || sortOrder > 999999) {
            throw new InvalidRequestException("sortOrder 超出允许范围");
        }
    }
}
