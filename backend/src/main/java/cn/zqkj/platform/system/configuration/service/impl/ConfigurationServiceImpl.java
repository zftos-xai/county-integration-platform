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
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import cn.zqkj.platform.system.configuration.domain.dto.CreateDictionaryItemCommand;
import cn.zqkj.platform.system.configuration.domain.dto.CreateDictionaryTypeCommand;
import cn.zqkj.platform.system.configuration.domain.dto.CreateExternalEndpointRequest;
import cn.zqkj.platform.system.configuration.domain.dto.CreateExternalSystemRequest;
import cn.zqkj.platform.system.configuration.domain.dto.DeleteParameterCommand;
import cn.zqkj.platform.system.configuration.domain.dto.ExternalEndpointAuthenticationCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateDictionaryItemCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateDictionaryTypeCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateExternalEndpointRequest;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateExternalSystemRequest;
import cn.zqkj.platform.system.configuration.domain.dto.UpsertParameterCommand;
import cn.zqkj.platform.system.configuration.domain.model.DictionaryItem;
import cn.zqkj.platform.system.configuration.domain.model.DictionaryType;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointAuthentication;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointVerificationStatus;
import cn.zqkj.platform.system.configuration.domain.model.ExternalSystem;
import cn.zqkj.platform.system.configuration.domain.model.ParameterDefinition;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.configuration.domain.model.ParameterValue;
import cn.zqkj.platform.system.configuration.domain.model.ParameterValueType;
import cn.zqkj.platform.system.configuration.domain.vo.DictionaryItemVO;
import cn.zqkj.platform.system.configuration.domain.vo.DictionaryTypeVO;
import cn.zqkj.platform.system.configuration.domain.vo.ExternalEndpointAuthenticationVO;
import cn.zqkj.platform.system.configuration.domain.vo.ExternalEndpointVO;
import cn.zqkj.platform.system.configuration.domain.vo.ExternalSystemVO;
import cn.zqkj.platform.system.configuration.domain.vo.ParameterDefinitionVO;
import cn.zqkj.platform.system.configuration.domain.vo.ParameterValueVO;
import cn.zqkj.platform.system.configuration.mapper.ConfigurationMapper;
import cn.zqkj.platform.system.configuration.service.ConfigurationService;
import cn.zqkj.platform.system.configuration.service.ParameterDefinitionRegistry;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.organization.service.OrganizationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 实现代码注册参数、适用范围校验和平台系统字典管理规则。
 */
@Service
public class ConfigurationServiceImpl implements ConfigurationService {

    private static final String MASKED_VALUE = "******";

    private final ConfigurationMapper mapper;
    private final ParameterDefinitionRegistry registry;
    private final OrganizationService organizationService;
    private final ManagementAuditService auditService;
    private final ExternalEndpointCredentialCipher credentialCipher;
    private final PhisEndpointVerificationService phisEndpointVerificationService;
    private final TransactionTemplate transactions;

    /**
     * 创建平台配置服务。
     *
     * @param mapper 配置Mapper
     * @param registry 代码注册参数清单
     * @param organizationService 机构查询服务
     * @param auditService 管理审计服务
     * @param credentialCipher 机构接口认证信息加密器
     * @param phisEndpointVerificationService 基层HIS端点自动校验服务
     * @param transactions 保存校验结果与审计的短事务
     */
    public ConfigurationServiceImpl(
            ConfigurationMapper mapper,
            ParameterDefinitionRegistry registry,
            OrganizationService organizationService,
            ManagementAuditService auditService,
            ExternalEndpointCredentialCipher credentialCipher,
            PhisEndpointVerificationService phisEndpointVerificationService,
            TransactionTemplate transactions
    ) {
        this.mapper = mapper;
        this.registry = registry;
        this.organizationService = organizationService;
        this.auditService = auditService;
        this.credentialCipher = credentialCipher;
        this.phisEndpointVerificationService = phisEndpointVerificationService;
        this.transactions = transactions;
    }


    /** 读取代码登记的可配置参数定义。 */
    @Transactional(readOnly = true)
    @Override
    public List<ParameterDefinitionVO> findParameterDefinitions() {
        return registry.findAll().stream().map(this::toDefinitionVO).toList();
    }

    /** 读取全局参数及获准机构参数，并隐藏敏感值。 */
    @Transactional(readOnly = true)
    @Override
    public List<ParameterValueVO> findParameterValues(AccessActor actor) {
        return mapper.findParameterValues(List.copyOf(actor.organizationCodes())).stream()
                .map(value -> toParameterValueVO(value, requireDefinition(value.parameterKey())))
                .toList();
    }

    /**
     * 按登记的参数规则保存指定环境和机构的参数值。
     *
     * <p>校验参数定义、适用范围和值类型后执行新增或乐观锁更新；审计摘要不包含参数值。</p>
     */
    @Transactional
    @Override
    public ParameterValueVO upsertParameter(String key, UpsertParameterCommand command, AccessActor actor) {
        ParameterDefinition definition = requireDefinition(key);
        validateParameterScope(definition, command.environment(), command.organizationId(), actor);
        String normalizedValue = validateAndNormalizeValue(definition, command.value());
        UpsertParameterCommand normalized = new UpsertParameterCommand(
                command.environment(), command.organizationId(), normalizedValue, command.enabled(),
                command.expectedVersion()
        );
        ParameterValue current = mapper.findParameterValue(definition.key(), command.environment(), command.organizationId()).orElse(null);
        long id;
        if (current == null) {
            if (normalized.expectedVersion() != null) {
                throw new ResourceConflictException("参数值尚未创建，请刷新后重试");
            }
            id = mapper.createParameterValue(
                    definition.key(), definition.valueType().name(), normalized, actor.loginName()
            );
        } else {
            if (normalized.expectedVersion() == null) {
                throw new ResourceConflictException("参数值已存在，更新必须使用最近读取的版本");
            }
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
                || !Objects.equals(saved.organizationId(), normalized.organizationId())) {
            throw new IllegalStateException("保存参数后读取到了不符合环境和机构条件的记录");
        }
        ParameterValueVO result = toParameterValueVO(saved, definition);
        audit(actor, result.organizationId(), result.organizationCode(),
                current == null ? "PARAMETER_CREATED" : "PARAMETER_UPDATED", "PARAMETER", definition.key(),
                "已保存参数适用范围、环境和启用状态；参数值未写入审计摘要");
        return result;
    }

    /**
     * 按范围和行版本删除一个参数配置。
     *
     * <p>精确匹配环境和机构范围并使用行版本删除；审计摘要不包含被删除的参数值。</p>
     */
    @Transactional
    @Override
    public void deleteParameter(String key, DeleteParameterCommand command, AccessActor actor) {
        ParameterDefinition definition = requireDefinition(key);
        validateParameterScope(definition, command.environment(), command.organizationId(), actor);
        ParameterValue current = mapper.findParameterValue(definition.key(), command.environment(), command.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("要删除的参数配置不存在"));
        if (mapper.deleteParameterValue(current.id(), command.expectedVersion()) != 1) {
            throw new ResourceConflictException("参数配置已被他人修改，请刷新后重试");
        }
        audit(actor, current.organizationId(), current.organizationCode(),
                "PARAMETER_DELETED", "PARAMETER", definition.key(),
                "已删除指定环境和机构范围的参数配置；参数值未写入审计摘要");
    }

    /** 读取平台系统字典类型，不包含 HIS 业务目录。 */
    @Transactional(readOnly = true)
    @Override
    public List<DictionaryTypeVO> findDictionaryTypes() {
        return mapper.findDictionaryTypes().stream().map(this::toDictionaryTypeVO).toList();
    }

    /** 创建代码唯一的平台字典类型，并追加审计。 */
    @Transactional
    @Override
    public DictionaryTypeVO createDictionaryType(CreateDictionaryTypeCommand command, AccessActor actor) {
        String typeCode = command.typeCode();
        if (mapper.dictionaryTypeCodeExists(typeCode)) {
            throw new ResourceConflictException("字典类型代码已存在");
        }
        DictionaryTypeVO result = toDictionaryTypeVO(requireDictionaryType(
                mapper.createDictionaryType(command, actor.loginName())
        ));
        audit(actor, null, null, "DICTIONARY_TYPE_CREATED", "DICTIONARY_TYPE", result.typeCode(),
                "已创建平台系统字典类型");
        return result;
    }

    /** 按行版本修改字典类型的名称、说明和启用状态。 */
    @Transactional
    @Override
    public DictionaryTypeVO updateDictionaryType(
            long typeId,
            UpdateDictionaryTypeCommand command,
            AccessActor actor
    ) {
        requireDictionaryType(typeId);
        if (mapper.updateDictionaryType(typeId, command, actor.loginName()) != 1) {
            throw new ResourceConflictException("字典类型已被他人修改，请刷新后重试");
        }
        DictionaryTypeVO result = toDictionaryTypeVO(requireDictionaryType(typeId));
        audit(actor, null, null, "DICTIONARY_TYPE_UPDATED", "DICTIONARY_TYPE", result.typeCode(),
                "已修改字典类型展示信息和启用状态");
        return result;
    }

    /**
     * 删除没有字典项且行版本未变化的字典类型。
     *
     * <p>仅允许删除不含任何字典项的类型；计数检查与乐观锁共同防止并发新增造成误删。</p>
     */
    @Transactional
    @Override
    public void deleteDictionaryType(long typeId, byte[] expectedVersion, AccessActor actor) {
        DictionaryType current = requireDictionaryType(typeId);
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

    /** 读取指定字典类型的字典项，可选择包含停用项。 */
    @Transactional(readOnly = true)
    @Override
    public List<DictionaryItemVO> findDictionaryItems(long typeId, boolean includeDisabled) {
        requireDictionaryType(typeId);
        return mapper.findDictionaryItems(typeId, includeDisabled).stream().map(this::toDictionaryItemVO).toList();
    }

    /** 向启用的字典类型新增代码唯一的字典项。 */
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
        String itemCode = command.itemCode();
        if (mapper.dictionaryItemCodeExists(typeId, itemCode)) {
            throw new ResourceConflictException("该字典类型中已存在相同的字典项代码");
        }
        DictionaryItemVO result = toDictionaryItemVO(requireDictionaryItem(
                mapper.createDictionaryItem(typeId, command, actor.loginName())
        ));
        audit(actor, null, null, "DICTIONARY_ITEM_CREATED", "DICTIONARY_ITEM",
                type.typeCode() + ":" + result.itemCode(), "已创建系统字典项");
        return result;
    }

    /** 按行版本修改字典项的展示文本、顺序和启用状态。 */
    @Transactional
    @Override
    public DictionaryItemVO updateDictionaryItem(
            long itemId,
            UpdateDictionaryItemCommand command,
            AccessActor actor
    ) {
        DictionaryItem current = requireDictionaryItem(itemId);
        DictionaryType type = requireDictionaryType(current.dictionaryTypeId());
        if (mapper.updateDictionaryItem(itemId, command, actor.loginName()) != 1) {
            throw new ResourceConflictException("字典项已被他人修改，请刷新后重试");
        }
        DictionaryItemVO result = toDictionaryItemVO(requireDictionaryItem(itemId));
        audit(actor, null, null, "DICTIONARY_ITEM_UPDATED", "DICTIONARY_ITEM",
                type.typeCode() + ":" + result.itemCode(),
                "已修改字典项文本、顺序和启用状态");
        return result;
    }

    /**
     * 删除未被业务引用且行版本未变化的字典项。
     *
     * <p>先检查业务引用再使用行版本删除；已经被使用的字典项只能停用，不能破坏历史含义。</p>
     */
    @Transactional
    @Override
    public void deleteDictionaryItem(long itemId, byte[] expectedVersion, AccessActor actor) {
        DictionaryItem current = requireDictionaryItem(itemId);
        DictionaryType type = requireDictionaryType(current.dictionaryTypeId());
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

    /** 读取外部系统的身份和启用状态，不返回地址和认证信息。 */
    @Transactional(readOnly = true)
    @Override
    public List<ExternalSystemVO> findExternalSystems() {
        return mapper.findExternalSystems().stream().map(this::toExternalSystemVO).toList();
    }

    /** 创建代码唯一的外部系统身份，并追加审计。 */
    @Transactional
    @Override
    public ExternalSystemVO createExternalSystem(CreateExternalSystemRequest command, AccessActor actor) {
        String code = command.systemCode();
        if (mapper.externalSystemCodeExists(code)) {
            throw new ResourceConflictException("外部系统代码已存在");
        }
        ExternalSystemVO result = toExternalSystemVO(requireExternalSystem(
                mapper.createExternalSystem(command, actor.loginName())
        ));
        audit(actor, null, null, "EXTERNAL_SYSTEM_CREATED", "EXTERNAL_SYSTEM", result.systemCode(),
                "已创建外部系统身份；未记录连接地址或认证信息");
        return result;
    }

    /** 按行版本修改外部系统展示信息和启用状态，保持系统代码不变。 */
    @Transactional
    @Override
    public ExternalSystemVO updateExternalSystem(long systemId, UpdateExternalSystemRequest command, AccessActor actor) {
        requireExternalSystem(systemId);
        if (mapper.updateExternalSystem(systemId, command, actor.loginName()) != 1) {
            throw new ResourceConflictException("外部系统资料已被他人修改，请刷新后重试");
        }
        ExternalSystemVO result = toExternalSystemVO(requireExternalSystem(systemId));
        audit(actor, null, null, "EXTERNAL_SYSTEM_UPDATED", "EXTERNAL_SYSTEM", result.systemCode(),
                "已修改外部系统展示信息和启用状态");
        return result;
    }

    /** 读取系统的全局端点和获准机构端点，不返回认证正文。 */
    @Transactional(readOnly = true)
    @Override
    public List<ExternalEndpointVO> findExternalEndpoints(long systemId, AccessActor actor) {
        requireExternalSystem(systemId);
        return mapper.findExternalEndpoints(systemId, List.copyOf(actor.organizationCodes())).stream()
                .map(this::toExternalEndpointVO)
                .toList();
    }

    /**
     * 创建外部系统端点并加密保存初次认证信息。
     *
     * <p>校验系统状态、机构范围和作用域唯一性后，加密保存认证信息；新端点必须完成自动验证后才能用于同步。</p>
     */
    @Transactional
    @Override
    public ExternalEndpointVO createExternalEndpoint(
            long systemId,
            CreateExternalEndpointRequest command,
            AccessActor actor
    ) {
        ExternalSystem system = requireExternalSystem(systemId);
        if (!system.enabled()) {
            throw new ResourceConflictException("外部系统已停用，不能新增服务地址");
        }
        if (command.organizationId() != null) {
            organizationService.getVisible(command.organizationId(), List.copyOf(actor.organizationCodes()));
        }
        ExternalEndpointAuthenticationCommand authentication = mergeAuthentication(null, command.authentication(), true);
        requirePrimaryHisOrganization(system, command.organizationId());
        if (mapper.externalEndpointScopeExists(systemId, null, command.environment(), command.organizationId())) {
            throw new ResourceConflictException("该外部系统、环境和机构已经配置了服务地址");
        }
        long id = mapper.createExternalEndpoint(systemId, command, actor.loginName());
        saveAuthentication(id, authentication, actor.loginName());
        ExternalEndpointVO result = toExternalEndpointVO(requireExternalEndpoint(id, List.copyOf(actor.organizationCodes())));
        audit(actor, result.organizationId(), result.organizationCode(), "EXTERNAL_ENDPOINT_CREATED",
                "EXTERNAL_ENDPOINT", String.valueOf(id),
                "已保存机构接口配置，需完成100-008自动校验后才可用于同步；地址和接入信息未写入审计摘要");
        return result;
    }

    /**
     * 为获准机构的配置管理员读取接入信息，并记录查看审计。
     *
     * <p>仅向有机构权限的配置管理员返回解密结果，并追加查看审计；认证正文不进入审计摘要。</p>
     */
    @Transactional
    @Override
    public ExternalEndpointAuthenticationVO findExternalEndpointAuthentication(
            long endpointId,
            AccessActor actor
    ) {
        ExternalEndpoint endpoint = requireExternalEndpoint(endpointId, List.copyOf(actor.organizationCodes()));
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
     * 按行版本更新端点和认证信息，同时使旧验证结果失效。
     *
     * <p>使用行版本更新端点和加密认证信息；任何影响连接或机构范围的修改都会使原验证结果失效。</p>
     */
    @Transactional
    @Override
    public ExternalEndpointVO updateExternalEndpoint(
            long endpointId,
            UpdateExternalEndpointRequest command,
            AccessActor actor
    ) {
        ExternalEndpoint current = requireExternalEndpoint(endpointId, List.copyOf(actor.organizationCodes()));
        if (command.organizationId() != null) {
            organizationService.getVisible(command.organizationId(), List.copyOf(actor.organizationCodes()));
        }
        boolean organizationChanged = !Objects.equals(current.organizationId(), command.organizationId());
        ExternalEndpointAuthenticationCommand authentication = mergeAuthentication(
                organizationChanged ? null : endpointId,
                command.authentication(),
                organizationChanged
        );
        requirePrimaryHisOrganization(requireExternalSystem(current.externalSystemId()), command.organizationId());
        if (mapper.externalEndpointScopeExists(current.externalSystemId(), endpointId, command.environment(), command.organizationId())) {
            throw new ResourceConflictException("该外部系统、环境和机构已经存在接口配置");
        }
        if (mapper.updateExternalEndpoint(endpointId, command, actor.loginName()) != 1) {
            throw new ResourceConflictException("服务地址资料已被他人修改，请刷新后重试");
        }
        if (authentication != null) {
            saveAuthentication(endpointId, authentication, actor.loginName());
        }
        ExternalEndpointVO result = toExternalEndpointVO(requireExternalEndpoint(endpointId, List.copyOf(actor.organizationCodes())));
        audit(actor, result.organizationId(), result.organizationCode(), "EXTERNAL_ENDPOINT_UPDATED",
                "EXTERNAL_ENDPOINT", String.valueOf(endpointId),
                "已修改机构接口配置，原自动校验结果已失效；需重新100-008校验后才可用于同步；地址和接入信息未写入审计摘要");
        return result;
    }

    /**
     * 在事务外校验HIS能力，再按原配置版本原子保存结果和审计。
     *
     * <p>校验期间配置被修改时拒绝写回，不能用旧调用结果启用新配置。</p>
     */
    @Override
    public ExternalEndpointVO verifyExternalEndpoint(long endpointId, AccessActor actor) {
        ExternalEndpoint endpoint = requireExternalEndpoint(endpointId, List.copyOf(actor.organizationCodes()));
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
        String verificationStep = "100-008";
        try {
            PhisEndpointVerificationResult verification = phisEndpointVerificationService.verify(
                    endpoint.organizationId(), endpoint.environment(), organizationName, endpoint.organizationCode(),
                    endpoint.sourceOrganizationId());
            if (!verification.verified()) {
                return markEndpointVerificationFailure(endpoint, ExternalEndpointVerificationStatus.FAILED,
                        verification.failureSummary(), actor);
            }
            return transactions.execute(status -> {
                if (mapper.markExternalEndpointVerified(endpointId,
                        verification.sourceOrganizationId(), verification.sourceOrganizationName(),
                        endpoint.version(), actor.loginName()) != 1) {
                    throw new ResourceConflictException("校验期间接口配置已修改，请重新读取后再校验");
                }
                ExternalEndpointVO result = toExternalEndpointVO(
                        requireExternalEndpoint(endpointId, List.copyOf(actor.organizationCodes())));
                audit(actor, result.organizationId(), result.organizationCode(), "PRIMARY_HIS_ENDPOINT_VERIFIED",
                        "EXTERNAL_ENDPOINT", String.valueOf(endpointId),
                        "100-008已确认唯一来源机构，100-003医院综合目录查询能力已验证");
                return result;
            });
        } catch (PhisCommunicationException | PhisProtocolException exception) {
            return markEndpointVerificationFailure(endpoint, ExternalEndpointVerificationStatus.RESULT_UNKNOWN,
                    "未能确认" + verificationStep + "处理结果，请先查看HIS交易记录，不要直接重复提交", actor);
        } catch (PhisConfigurationException exception) {
            return markEndpointVerificationFailure(endpoint, ExternalEndpointVerificationStatus.FAILED,
                    "HIS接口配置不可用", actor);
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
                definition.environments().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet()),
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
     * @param environment 目标环境
     * @param organizationId 可选目标机构
     * @param actor 操作人
     */
    private void validateParameterScope(
            ParameterDefinition definition,
            ParameterEnvironment environment,
            Long organizationId,
            AccessActor actor
    ) {
        if (!definition.environments().contains(environment)) {
            throw new InvalidRequestException("参数不能用于所选环境");
        }
        if (definition.organizationScoped()) {
            if (organizationId == null) {
                throw new InvalidRequestException("机构专用参数必须提供 organizationId");
            }
            organizationService.getVisible(organizationId, List.copyOf(actor.organizationCodes()));
        } else if (organizationId != null) {
            throw new InvalidRequestException("全局参数不能包含 organizationId");
        }
    }

    /**
     * 按代码注册类型和边界校验并规范化参数值。
     *
     * @param definition 参数定义
     * @param value 已完成入口文本校验的参数值
     * @return 规范化值
     */
    private String validateAndNormalizeValue(ParameterDefinition definition, String value) {
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
        return mapper.findExternalSystem(systemId)
                .orElseThrow(() -> new ResourceNotFoundException("External system was not found"));
    }

    /**
     * 读取外部系统端点；不存在时抛出资源不存在异常。
     *
     * @param endpointId 外部服务地址主键
     * @return 已存在服务地址
     */
    private ExternalEndpoint requireExternalEndpoint(long endpointId, List<String> organizationCodes) {
        return mapper.findExternalEndpoint(endpointId, organizationCodes)
                .orElseThrow(() -> new ResourceNotFoundException("External endpoint was not found"));
    }

    /**
     * 对基层HIS机构配置强制要求一个平台机构；100-008查询条件由平台机构资料自动提供。
     *
     * @param system 外部系统
     * @param organizationId 端点绑定的平台机构主键
     */
    private void requirePrimaryHisOrganization(ExternalSystem system, Long organizationId) {
        if (!"PRIMARY_HIS".equals(system.systemCode())) {
            return;
        }
        if (organizationId == null) {
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
        return transactions.execute(status -> {
            if (mapper.markExternalEndpointVerificationFailed(endpoint.id(), verificationStatus.name(), summary,
                    endpoint.version(), actor.loginName()) != 1) {
                throw new ResourceConflictException("接口校验结果保存失败，请重新读取后再试");
            }
            ExternalEndpointVO result = toExternalEndpointVO(requireExternalEndpoint(endpoint.id(), List.copyOf(actor.organizationCodes())));
            auditService.append(new ManagementAuditCommand(
                    actor.userId(), actor.loginName(), result.organizationId(), result.organizationCode(),
                    "PRIMARY_HIS_ENDPOINT_VERIFICATION_" + verificationStatus.name(),
                    "EXTERNAL_ENDPOINT", String.valueOf(endpoint.id()), "FAILURE",
                    "HIS接口校验未通过，接口未启用：" + summary, auditService.currentRequestId()));
            return result;
        });
    }



    /**
     * 追加配置维护审计，不记录参数值、服务地址或认证正文。
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
        auditService.append(new ManagementAuditCommand(actor.userId(), actor.loginName(), organizationId, organizationCode,
                action, targetType, targetId, "SUCCESS", summary,
                auditService.currentRequestId()));
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
        String vendorCode = retainIfOmitted(submitted.vendorCode(), current == null ? null : current.vendorCode());
        String username = retainIfOmitted(submitted.username(), current == null ? null : current.username());
        String password = retainIfOmitted(submitted.password(), current == null ? null : current.password());
        String authorizationCode = retainIfOmitted(
                submitted.authorizationCode(),
                current == null ? null : current.authorizationCode()
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

    /** 空白提交值已由输入对象转为空值；未提交字段保留已有认证信息。 */
    private String retainIfOmitted(String submitted, String current) {
        if (submitted != null) return submitted;
        return Func.isBlank(current) ? null : current;
    }
}
