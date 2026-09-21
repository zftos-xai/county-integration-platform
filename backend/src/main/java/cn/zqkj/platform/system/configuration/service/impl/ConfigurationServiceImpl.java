package cn.zqkj.platform.system.configuration.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.exception.ResourceNotFoundException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.his.domain.endpointverification.model.PhisEndpointVerificationResult;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisConfigurationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.his.service.PhisEndpointVerificationService;
import cn.zqkj.platform.system.configuration.domain.dto.CreateDictionaryItemCommand;
import cn.zqkj.platform.system.configuration.domain.dto.CreateDictionaryTypeCommand;
import cn.zqkj.platform.system.configuration.domain.dto.DeleteParameterCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateDictionaryItemCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateDictionaryTypeCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpsertParameterCommand;
import cn.zqkj.platform.system.configuration.domain.dto.ExternalEndpointAuthenticationCommand;
import cn.zqkj.platform.system.configuration.domain.dto.ExternalEndpointCommand;
import cn.zqkj.platform.system.configuration.domain.dto.ExternalSystemCommand;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.configuration.domain.model.DictionaryItem;
import cn.zqkj.platform.system.configuration.domain.model.DictionaryType;
import cn.zqkj.platform.system.configuration.domain.model.ParameterDefinition;
import cn.zqkj.platform.system.configuration.domain.model.ParameterValue;
import cn.zqkj.platform.system.configuration.domain.model.ParameterValueType;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointAuthentication;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointVerificationStatus;
import cn.zqkj.platform.system.configuration.domain.model.ExternalSystem;
import cn.zqkj.platform.system.configuration.domain.vo.DictionaryItemVO;
import cn.zqkj.platform.system.configuration.domain.vo.DictionaryTypeVO;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.configuration.domain.vo.ParameterDefinitionVO;
import cn.zqkj.platform.system.configuration.domain.vo.ParameterValueVO;
import cn.zqkj.platform.system.configuration.domain.vo.ExternalEndpointVO;
import cn.zqkj.platform.system.configuration.domain.vo.ExternalEndpointAuthenticationVO;
import cn.zqkj.platform.system.configuration.domain.vo.ExternalSystemVO;
import cn.zqkj.platform.system.configuration.mapper.ConfigurationMapper;
import cn.zqkj.platform.system.configuration.service.ConfigurationService;
import cn.zqkj.platform.system.organization.service.OrganizationService;
import cn.zqkj.platform.system.configuration.service.ParameterDefinitionRegistry;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
    private final ExternalEndpointCredentialCipher credentialCipher;
    private final PhisEndpointVerificationService phisEndpointVerificationService;

    /**
     * 创建平台配置服务。
     *
     * @param mapper 配置Mapper
     * @param registry 代码注册参数清单
     * @param organizationService 机构查询服务
     * @param auditService 管理审计服务
     * @param credentialCipher 机构接口认证信息加密器
     * @param phisEndpointVerificationService 基层HIS端点自动校验服务
     */
    @Autowired
    public ConfigurationServiceImpl(
            ConfigurationMapper mapper,
            ParameterDefinitionRegistry registry,
            OrganizationService organizationService,
            ManagementAuditService auditService,
            ExternalEndpointCredentialCipher credentialCipher,
            PhisEndpointVerificationService phisEndpointVerificationService
    ) {
        this.mapper = mapper;
        this.registry = registry;
        this.organizationService = organizationService;
        this.auditService = auditService;
        this.credentialCipher = credentialCipher;
        this.phisEndpointVerificationService = phisEndpointVerificationService;
    }

    /**
     * 兼容不涉及HIS校验的既有单元测试构造方式。
     *
     * @param mapper 配置Mapper
     * @param registry 代码注册参数清单
     * @param organizationService 机构查询服务
     * @param auditService 管理审计服务
     * @param credentialCipher 机构接口认证信息加密器
     */
    public ConfigurationServiceImpl(
            ConfigurationMapper mapper,
            ParameterDefinitionRegistry registry,
            OrganizationService organizationService,
            ManagementAuditService auditService,
            ExternalEndpointCredentialCipher credentialCipher
    ) {
        this(mapper, registry, organizationService, auditService, credentialCipher,
                (PhisEndpointVerificationService) null);
    }

    /** {@inheritDoc} 实际可配置项以代码注册表为准，不从数据库反向推导定义。 */
    @Transactional(readOnly = true)
    @Override
    public List<ParameterDefinitionVO> findParameterDefinitions() {
        return registry.findAll().stream().map(this::toDefinitionVO).toList();
    }

    /** {@inheritDoc} 返回前按操作人的机构范围过滤机构级参数。 */
    @Transactional(readOnly = true)
    @Override
    public List<ParameterValueVO> findParameterValues(AccessActor actor) {
        return mapper.findParameterValues().stream()
                .filter(value -> value.organizationCode() == null || actor.canAccess(value.organizationCode()))
                .map(value -> toParameterValueVO(value, requireDefinition(value.parameterKey())))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>校验参数定义、适用范围和值类型后执行新增或乐观锁更新；审计摘要不包含参数值。</p>
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>精确匹配环境和机构范围并使用行版本删除；审计摘要不包含被删除的参数值。</p>
     */
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

    /** {@inheritDoc} 仅返回平台系统字典，不混入HIS业务目录。 */
    @Transactional(readOnly = true)
    @Override
    public List<DictionaryTypeVO> findDictionaryTypes() {
        return mapper.findDictionaryTypes().stream().map(this::toDictionaryTypeVO).toList();
    }

    /** {@inheritDoc} 规范化类型代码并在同一事务完成创建和审计。 */
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

    /** {@inheritDoc} 使用行版本更新名称、说明和启用状态，并追加成功审计。 */
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

    /**
     * {@inheritDoc}
     *
     * <p>仅允许删除不含任何字典项的类型；计数检查与乐观锁共同防止并发新增造成误删。</p>
     */
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

    /** {@inheritDoc} 查询前确认字典类型存在，并按调用方选择决定是否包含停用项。 */
    @Transactional(readOnly = true)
    @Override
    public List<DictionaryItemVO> findDictionaryItems(long typeId, boolean includeDisabled) {
        requireDictionaryType(typeId);
        return mapper.findDictionaryItems(typeId, includeDisabled).stream().map(this::toDictionaryItemVO).toList();
    }

    /** {@inheritDoc} 仅允许向启用的字典类型添加代码唯一、排序值合法的字典项。 */
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

    /** {@inheritDoc} 使用行版本更新展示文本、顺序和启用状态，不允许修改稳定代码。 */
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

    /**
     * {@inheritDoc}
     *
     * <p>先检查业务引用再使用行版本删除；已经被使用的字典项只能停用，不能破坏历史含义。</p>
     */
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

    /** {@inheritDoc} 返回外部系统身份和启用状态，不包含端点地址或认证信息。 */
    @Transactional(readOnly = true)
    @Override
    public List<ExternalSystemVO> findExternalSystems() {
        return mapper.findExternalSystems().stream().map(this::toExternalSystemVO).toList();
    }

    /** {@inheritDoc} 规范化稳定系统代码并创建外部系统身份，审计不记录连接信息。 */
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
                "已创建外部系统身份；未记录连接地址或认证信息");
        return result;
    }

    /** {@inheritDoc} 使用行版本更新展示信息和启用状态，稳定系统代码保持不变。 */
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

    /** {@inheritDoc} 按操作人的机构范围过滤端点，并以脱敏视图返回。 */
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

    /**
     * {@inheritDoc}
     *
     * <p>校验系统状态、机构范围和作用域唯一性后，加密保存认证信息；新端点必须完成自动验证后才能用于同步。</p>
     */
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
        ExternalEndpointAuthenticationCommand authentication = mergeAuthentication(null, command.authentication(), true);
        ExternalEndpointCommand normalized = normalizeEndpoint(command, authentication, false, null);
        requirePrimaryHisOrganization(system, normalized);
        if (mapper.externalEndpointScopeExists(systemId, null, normalized)) {
            throw new ResourceConflictException("该外部系统、环境和机构已经配置了服务地址");
        }
        long id = mapper.createExternalEndpoint(systemId, normalized, requireActor(actor.loginName()));
        saveAuthentication(id, authentication, requireActor(actor.loginName()));
        ExternalEndpointVO result = toExternalEndpointVO(requireExternalEndpoint(id));
        audit(actor, result.organizationId(), result.organizationCode(), "EXTERNAL_ENDPOINT_CREATED",
                "EXTERNAL_ENDPOINT", String.valueOf(id),
                "已保存机构接口配置，需完成100-008自动校验后才可用于同步；地址和接入信息未写入审计摘要");
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>仅向有机构权限的配置管理员返回解密结果，并追加查看审计；认证正文不进入审计摘要。</p>
     */
    @Transactional
    @Override
    public ExternalEndpointAuthenticationVO findExternalEndpointAuthentication(
            long endpointId,
            AccessActor actor
    ) {
        ExternalEndpoint endpoint = requireExternalEndpoint(endpointId);
        requireEndpointAccess(endpoint, actor);
        ExternalEndpointAuthentication authentication = mapper.findExternalEndpointCredential(endpointId)
                .map(credentialCipher::decrypt)
                .orElseThrow(() -> new ResourceNotFoundException("该机构尚未保存HIS接入信息"));
        audit(actor, endpoint.organizationId(), endpoint.organizationCode(),
                "EXTERNAL_ENDPOINT_AUTHENTICATION_VIEWED", "EXTERNAL_ENDPOINT", String.valueOf(endpointId),
                "已在配置编辑页查看机构HIS接入信息；接入内容未写入审计摘要");
        return new ExternalEndpointAuthenticationVO(
                authentication.vendorCode(), Objects.requireNonNullElse(authentication.username(), ""),
                Objects.requireNonNullElse(authentication.password(), ""), authentication.authorizationCode()
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>使用行版本更新端点和加密认证信息；任何影响连接或机构范围的修改都会使原验证结果失效。</p>
     */
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
        validateEndpointScope(command, actor);
        boolean organizationChanged = !Objects.equals(current.organizationId(), command.organizationId());
        ExternalEndpointAuthenticationCommand authentication = mergeAuthentication(
                organizationChanged ? null : endpointId,
                command.authentication(),
                organizationChanged
        );
        ExternalEndpointCommand normalized = normalizeEndpoint(
                command, authentication, false, command.expectedVersion()
        );
        requirePrimaryHisOrganization(requireExternalSystem(current.externalSystemId()), normalized);
        if (mapper.externalEndpointScopeExists(current.externalSystemId(), endpointId, normalized)) {
            throw new ResourceConflictException("该外部系统、环境和机构已经存在接口配置");
        }
        if (mapper.updateExternalEndpoint(endpointId, normalized, requireActor(actor.loginName())) != 1) {
            throw new ResourceConflictException("服务地址资料已被他人修改，请刷新后重试");
        }
        if (authentication != null) {
            saveAuthentication(endpointId, authentication, requireActor(actor.loginName()));
        }
        ExternalEndpointVO result = toExternalEndpointVO(requireExternalEndpoint(endpointId));
        audit(actor, result.organizationId(), result.organizationCode(), "EXTERNAL_ENDPOINT_UPDATED",
                "EXTERNAL_ENDPOINT", String.valueOf(endpointId),
                "已修改机构接口配置，原自动校验结果已失效；需重新100-008校验后才可用于同步；地址和接入信息未写入审计摘要");
        return result;
    }

    /**
     * 以同一事务保存100-008机构确认、100-003能力验证结果和成功审计记录。
     *
     * <p>HIS调用先于本地写入发生；事务在调用期间不修改本地数据，避免校验成功却因审计写入失败而
     * 向调用方返回不确定结果。</p>
     *
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public ExternalEndpointVO verifyExternalEndpoint(long endpointId, AccessActor actor) {
        ExternalEndpoint endpoint = requireExternalEndpoint(endpointId);
        requireEndpointAccess(endpoint, actor);
        if (!"PRIMARY_HIS".equals(requireExternalSystem(endpoint.externalSystemId()).systemCode())) {
            throw new InvalidRequestException("只有基层HIS接口需要执行机构自动校验");
        }
        if (endpoint.organizationId() == null) {
            throw new InvalidRequestException("基层HIS接口必须绑定到一个平台机构");
        }
        if (!endpoint.credentialConfigured()) {
            throw new InvalidRequestException("请先保存该机构的HIS接入信息，再执行接口校验");
        }
        OrganizationVO organization = organizationService.get(endpoint.organizationId());
        String organizationName = organization == null ? null : organization.organizationName();
        if (phisEndpointVerificationService == null) {
            throw new IllegalStateException("基层HIS校验服务未装配");
        }
        String verificationStep = "100-008";
        try {
            PhisEndpointVerificationResult verification = phisEndpointVerificationService.verify(
                    endpoint.organizationId(), endpoint.environment(), organizationName, endpoint.organizationCode(),
                    endpoint.sourceOrganizationId());
            if (!verification.verified()) {
                return markEndpointVerificationFailure(endpoint, ExternalEndpointVerificationStatus.FAILED,
                        verification.failureSummary(), actor);
            }
            if (mapper.markExternalEndpointVerified(endpointId,
                    verification.sourceOrganizationId(), verification.sourceOrganizationName(),
                    requireActor(actor.loginName())) != 1) {
                throw new ResourceConflictException("接口校验结果保存失败，请重新读取后再试");
            }
            ExternalEndpointVO result = toExternalEndpointVO(requireExternalEndpoint(endpointId));
            audit(actor, result.organizationId(), result.organizationCode(), "PRIMARY_HIS_ENDPOINT_VERIFIED",
                "EXTERNAL_ENDPOINT", String.valueOf(endpointId),
                    "100-008已确认唯一来源机构，100-003医院综合目录查询能力已验证");
            return result;
        } catch (PhisCommunicationException | PhisProtocolException exception) {
            return markEndpointVerificationFailure(endpoint, ExternalEndpointVerificationStatus.RESULT_UNKNOWN,
                    "未能确认" + verificationStep + "处理结果，请先查看HIS交易记录，不要直接重复提交", actor);
        } catch (PhisConfigurationException exception) {
            return markEndpointVerificationFailure(endpoint, ExternalEndpointVerificationStatus.FAILED,
                    safeVerificationMessage(exception.getMessage(), "HIS接口配置不可用"), actor);
        }
    }

    /**
     * 把代码注册参数定义转换为API输出。
     *
     * @param definition 参数定义
     * @return API元数据
     */
    private ParameterDefinitionVO toDefinitionVO(ParameterDefinition definition) {
        return new ParameterDefinitionVO(
                definition.key(), definition.name(), definition.valueType().name(),
                definition.environments().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                definition.organizationScoped(), definition.sensitive(), definition.maximumLength(),
                definition.minimumNumber(), definition.maximumNumber(), definition.pattern()
        );
    }

    /**
     * 把参数值快照转换为安全API输出，并隐藏敏感值。
     *
     * @param value 参数值
     * @param definition 参数定义
     * @return 安全输出
     */
    private ParameterValueVO toParameterValueVO(ParameterValue value, ParameterDefinition definition) {
        return new ParameterValueVO(
                value.id(), value.parameterKey(), value.valueType().name(), value.environment().name(),
                value.organizationId(), value.organizationCode(),
                definition.sensitive() ? MASKED_VALUE : value.value(), true, value.enabled(), value.updatedAt(),
                Func.encodeBase64(value.version())
        );
    }

    /**
     * 把字典类型快照转换为API输出。
     *
     * @param type 字典类型
     * @return API输出
     */
    private DictionaryTypeVO toDictionaryTypeVO(DictionaryType type) {
        return new DictionaryTypeVO(
                type.id(), type.typeCode(), type.typeName(), type.description(), type.enabled(),
                type.createdAt(), type.updatedAt(), Func.encodeBase64(type.version())
        );
    }

    /**
     * 把字典项快照转换为API输出。
     *
     * @param item 字典项
     * @return API输出
     */
    private DictionaryItemVO toDictionaryItemVO(DictionaryItem item) {
        return new DictionaryItemVO(
                item.id(), item.dictionaryTypeId(), item.itemCode(), item.itemLabel(), item.sortOrder(),
                item.enabled(), item.createdAt(), item.updatedAt(), Func.encodeBase64(item.version())
        );
    }

    /**
     * 把外部系统快照转换为API输出。
     *
     * @param system 外部系统
     * @return API输出
     */
    private ExternalSystemVO toExternalSystemVO(ExternalSystem system) {
        return new ExternalSystemVO(system.id(), system.systemCode(), system.systemName(), system.description(),
                system.enabled(), system.createdAt(), system.updatedAt(),
                Func.encodeBase64(system.version()));
    }

    /**
     * 把端点快照转换为不含认证秘密的API输出。
     *
     * @param endpoint 外部服务地址
     * @return 不含认证信息明文的API输出
     */
    private ExternalEndpointVO toExternalEndpointVO(ExternalEndpoint endpoint) {
        return new ExternalEndpointVO(endpoint.id(), endpoint.externalSystemId(), endpoint.environment().name(),
                endpoint.organizationId(), endpoint.organizationCode(), endpoint.baseUrl(),
                endpoint.connectTimeoutMs(), endpoint.readTimeoutMs(),
                endpoint.credentialConfigured(),
                endpoint.enabled(), endpoint.createdAt(), endpoint.updatedAt(),
                Func.encodeBase64(endpoint.version()),
                endpoint.sourceOrganizationId(), endpoint.sourceOrganizationName(),
                endpoint.verificationStatus().name(), endpoint.verifiedAt(), endpoint.verificationFailureSummary());
    }

    /**
     * 读取代码注册参数定义；未知参数键立即拒绝。
     *
     * @param key 参数键
     * @return 已注册定义
     */
    private ParameterDefinition requireDefinition(String key) {
        return registry.find(key)
                .orElseThrow(() -> new InvalidRequestException("Parameter key is not registered"));
    }

    /**
     * 校验参数环境、机构作用域和当前操作人范围。
     *
     * @param definition 参数定义
     * @param command 写入命令
     * @param actor 操作人
     */
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

    /**
     * 按代码注册类型和边界校验并规范化参数值。
     *
     * @param definition 参数定义
     * @param rawValue 原始值
     * @return 规范化值
     */
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

    /**
     * 按参数定义的完整正则约束校验文本值。
     *
     * @param expression 可选正则
     * @param value 文本值
     */
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

    /**
     * 读取字典类型；不存在时抛出资源不存在异常。
     *
     * @param typeId 类型主键
     * @return 存在的字典类型
     */
    private DictionaryType requireDictionaryType(long typeId) {
        if (typeId <= 0) {
            throw new InvalidRequestException("dictionaryTypeId 必须大于 0");
        }
        return mapper.findDictionaryType(typeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary type was not found"));
    }

    /**
     * 读取字典项；不存在时抛出资源不存在异常。
     *
     * @param itemId 字典项主键
     * @return 存在的字典项
     */
    private DictionaryItem requireDictionaryItem(long itemId) {
        if (itemId <= 0) {
            throw new InvalidRequestException("dictionaryItemId 必须大于 0");
        }
        return mapper.findDictionaryItem(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary item was not found"));
    }

    /**
     * 读取外部系统；不存在时抛出资源不存在异常。
     *
     * @param systemId 外部系统主键
     * @return 已存在系统
     */
    private ExternalSystem requireExternalSystem(long systemId) {
        if (systemId <= 0) {
            throw new InvalidRequestException("externalSystemId 必须大于 0");
        }
        return mapper.findExternalSystem(systemId)
                .orElseThrow(() -> new ResourceNotFoundException("External system was not found"));
    }

    /**
     * 读取外部系统端点；不存在时抛出资源不存在异常。
     *
     * @param endpointId 外部服务地址主键
     * @return 已存在服务地址
     */
    private ExternalEndpoint requireExternalEndpoint(long endpointId) {
        if (endpointId <= 0) {
            throw new InvalidRequestException("externalEndpointId 必须大于 0");
        }
        return mapper.findExternalEndpoint(endpointId)
                .orElseThrow(() -> new ResourceNotFoundException("External endpoint was not found"));
    }

    /**
     * 校验端点环境、机构范围、地址、超时和启用前提。
     *
     * @param command 服务地址命令
     * @param actor 操作人
     */
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

    /**
     * 校验当前操作人有权管理端点所属机构。
     *
     * @param endpoint 服务地址
     * @param actor 操作人
     */
    private void requireEndpointAccess(ExternalEndpoint endpoint, AccessActor actor) {
        if (endpoint.organizationCode() != null && !actor.canAccess(endpoint.organizationCode())) {
            throw new AccessDeniedException("当前账号无权访问该机构");
        }
    }

    /**
     * 规范化端点地址并拒绝凭证、片段和未知查询参数。
     *
     * @param command 原命令
     * @param enabled 是否启用
     * @param version 并发版本
     * @return 规范化命令
     */
    private ExternalEndpointCommand normalizeEndpoint(
            ExternalEndpointCommand command,
            ExternalEndpointAuthenticationCommand authentication,
            boolean enabled,
            byte[] version
    ) {
        String baseUrl = requireServiceUrl(command.baseUrl());
        if (command.connectTimeoutMs() < 100 || command.connectTimeoutMs() > 60000) {
            throw new InvalidRequestException("connectTimeoutMs 超出允许范围");
        }
        if (command.readTimeoutMs() < command.connectTimeoutMs() || command.readTimeoutMs() > 300000) {
            throw new InvalidRequestException("readTimeoutMs 超出允许范围");
        }
        return new ExternalEndpointCommand(command.environment(), command.organizationId(), baseUrl,
                command.connectTimeoutMs(), command.readTimeoutMs(), authentication, enabled, version);
    }

    /**
     * 对基层HIS机构配置强制要求一个平台机构；100-008查询条件由平台机构资料自动提供。
     *
     * @param system 外部系统
     * @param command 已规范化端点命令
     */
    private void requirePrimaryHisOrganization(ExternalSystem system, ExternalEndpointCommand command) {
        if (!"PRIMARY_HIS".equals(system.systemCode())) {
            return;
        }
        if (command.organizationId() == null) {
            throw new InvalidRequestException("基层HIS接口必须绑定到一个平台机构");
        }
    }

    /**
     * 保存100-008不能确认唯一机构的结果，并让端点保持不可参与业务调用的状态。
     *
     * @param endpoint 当前端点
     * @param verificationStatus 明确失败或结果未知状态
     * @param summary 面向管理员的安全摘要
     * @param actor 操作人
     * @return 回读后的端点输出
     */
    private ExternalEndpointVO markEndpointVerificationFailure(
            ExternalEndpoint endpoint,
            ExternalEndpointVerificationStatus verificationStatus,
            String summary,
            AccessActor actor
    ) {
        if (mapper.markExternalEndpointVerificationFailed(endpoint.id(), verificationStatus.name(), summary,
                requireActor(actor.loginName())) != 1) {
            throw new ResourceConflictException("接口校验结果保存失败，请重新读取后再试");
        }
        ExternalEndpointVO result = toExternalEndpointVO(requireExternalEndpoint(endpoint.id()));
        audit(actor, result.organizationId(), result.organizationCode(), "PRIMARY_HIS_ENDPOINT_VERIFICATION_"
                        + verificationStatus.name(),
                "EXTERNAL_ENDPOINT", String.valueOf(endpoint.id()),
                "100-008未确认唯一来源机构，接口未启用：" + summary);
        return result;
    }

    /**
     * 规范化可展示给管理员的100-008失败信息。
     *
     * @param message HIS返回或本地配置错误
     * @param fallback 默认提示
     * @return 不超过500字的安全摘要
     */
    private String safeVerificationMessage(String message, String fallback) {
        String value = Func.trimToNull(message);
        if (value == null) {
            return fallback;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    /**
     * 追加不包含DDL全文和连接信息的管理审计事件。
     *
     * @param actor 操作人
     * @param organizationId 机构主键
     * @param organizationCode 机构代码
     * @param action 动作
     * @param targetType 目标类型
     * @param targetId 目标标识
     * @param summary 不含敏感内容的摘要
     */
    private void audit(AccessActor actor, Long organizationId, String organizationCode, String action,
                       String targetType, String targetId, String summary) {
        auditService.recordSuccess(new ManagementAuditCommand(actor, null, organizationId, organizationCode,
                action, targetType, targetId, "SUCCESS", summary,
                auditService.currentRequestId()));
    }

    /**
     * 校验并规范化外部系统服务地址。
     *
     * <p>县域医疗系统存在正式发布的HTTP WebService，因此允许HTTP和HTTPS。兼容并保留用户从ASMX
     * 帮助页复制的{@code ?op=PHIS_Interface}完整地址，确保保存后能够原样回显；其他查询参数、用户信息
     * 和片段仍被拒绝。协议客户端仅在实际SOAP调用时移除操作页参数。</p>
     *
     * @param value 地址
     * @return 规范化HTTP或HTTPS地址
     */
    private String requireServiceUrl(String value) {
        String normalized = requireText(value, "baseUrl", 500);
        try {
            URI uri = new URI(normalized);
            boolean supportedScheme = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
            boolean supportedOperationQuery = uri.getQuery() == null
                    || "op=PHIS_Interface".equals(uri.getRawQuery());
            if (!supportedScheme || uri.getHost() == null || uri.getUserInfo() != null
                    || !supportedOperationQuery || uri.getFragment() != null) {
                throw new InvalidRequestException(
                        "baseUrl 必须是 HTTP 或 HTTPS 服务地址；只兼容op=PHIS_Interface操作参数"
                );
            }
            return uri.normalize().toASCIIString();
        } catch (URISyntaxException exception) {
            throw new InvalidRequestException("baseUrl 格式无效");
        }
    }

    /**
     * 合并并校验接口认证信息；修改时空字段保留原值。
     *
     * @param endpointId 修改时的服务地址主键
     * @param submitted 本次提交内容
     * @param required 是否必须提交
     * @return 需要保存的新完整认证信息；无需修改时为空
     */
    private ExternalEndpointAuthenticationCommand mergeAuthentication(
            Long endpointId,
            ExternalEndpointAuthenticationCommand submitted,
            boolean required
    ) {
        if (submitted == null) {
            if (required) {
                throw new InvalidRequestException("首次配置必须填写接口认证信息");
            }
            return null;
        }
        ExternalEndpointAuthentication current = endpointId == null ? null
                : mapper.findExternalEndpointCredential(endpointId).map(credentialCipher::decrypt).orElse(null);
        String vendorCode = retained(submitted.vendorCode(), current == null ? null : current.vendorCode(), 100);
        String username = retained(submitted.username(), current == null ? null : current.username(), 100);
        String password = retained(submitted.password(), current == null ? null : current.password(), 200);
        String authorizationCode = retained(
                submitted.authorizationCode(),
                current == null ? null : current.authorizationCode(),
                200
        );
        if (vendorCode == null || authorizationCode == null) {
            throw new InvalidRequestException("厂商编号和机构授权码不能为空");
        }
        if ((username == null) != (password == null)) {
            throw new InvalidRequestException("接口用户名和接口密码必须同时填写");
        }
        return new ExternalEndpointAuthenticationCommand(vendorCode, username, password, authorizationCode);
    }

    /**
     * 加密并新增或替换端点认证信息。
     *
     * @param endpointId 服务地址主键
     * @param authentication 完整认证信息
     * @param actor 操作人
     */
    private void saveAuthentication(
            long endpointId,
            ExternalEndpointAuthenticationCommand authentication,
            String actor
    ) {
        var encrypted = credentialCipher.encrypt(endpointId, new ExternalEndpointAuthentication(
                authentication.vendorCode(),
                authentication.username(),
                authentication.password(),
                authentication.authorizationCode()
        ));
        if (mapper.findExternalEndpointCredential(endpointId).isPresent()) {
            if (mapper.updateExternalEndpointCredential(endpointId, encrypted, actor) != 1) {
                throw new ResourceConflictException("接口认证信息已变化，请刷新后重试");
            }
        } else {
            mapper.createExternalEndpointCredential(endpointId, encrypted, actor);
        }
    }

    /**
     * 从现有密文恢复未在更新请求中重新提交的认证字段。
     *
     * @param submitted 本次提交值
     * @param current 已保存值
     * @param maximumLength 最大长度
     * @return 规范化后的保留或新值
     */
    private String retained(String submitted, String current, int maximumLength) {
        if (submitted == null || submitted.isBlank()) {
            return current == null || current.isBlank() ? null : current;
        }
        String normalized = submitted.trim();
        if (normalized.length() > maximumLength) {
            throw new InvalidRequestException("接口认证信息超过允许长度");
        }
        return normalized;
    }

    /**
     * 规范化稳定业务代码并拒绝空白。
     *
     * @param value 代码
     * @param field 字段名
     * @return 规范化代码
     */
    private String normalizeCode(String value, String field) {
        String code = requireText(value, field, 64).toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new InvalidRequestException(field + " has an invalid format");
        }
        return code;
    }

    /**
     * 规范化代码注册的参数键。
     *
     * @param value 参数键
     * @return 规范化的小写参数键
     */
    private String normalizeParameterKey(String value) {
        String key = requireText(value, "parameterKey", 64).toLowerCase(Locale.ROOT);
        if (!PARAMETER_KEY_PATTERN.matcher(key).matches()) {
            throw new InvalidRequestException("parameterKey 格式无效");
        }
        return key;
    }

    /**
     * 校验必填文本并返回裁剪后的值。
     *
     * @param value 文本
     * @param field 字段名
     * @param maximumLength 最大长度
     * @return 裁剪值
     */
    private String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.trim().length() > maximumLength) {
            throw new InvalidRequestException(field + " is invalid");
        }
        return value.trim();
    }

    /**
     * 校验服务层收到可信且完整的操作人。
     *
     * @param actor 操作人
     * @return 裁剪后的操作人
     */
    private String requireActor(String actor) {
        return requireText(actor, "actor", 64);
    }

    /**
     * 校验SQL Server行版本恰好为8字节。
     *
     * @param version SQL Server并发版本
     */
    private void requireVersion(byte[] version) {
        if (version == null || version.length != Long.BYTES) {
            throw new InvalidRequestException("version 必须是 8 字节的 SQL Server 行版本号");
        }
    }

    /**
     * 校验字典项排序值在数据库约束范围内。
     *
     * @param sortOrder 展示顺序
     */
    private void validateSortOrder(int sortOrder) {
        if (sortOrder < 0 || sortOrder > 999999) {
            throw new InvalidRequestException("sortOrder 超出允许范围");
        }
    }
}
