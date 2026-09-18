package cn.zqkj.platform.system.service;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.system.domain.dto.CreateDictionaryItemCommand;
import cn.zqkj.platform.system.domain.dto.CreateDictionaryTypeCommand;
import cn.zqkj.platform.system.domain.dto.DeleteParameterCommand;
import cn.zqkj.platform.system.domain.dto.UpdateDictionaryTypeCommand;
import cn.zqkj.platform.system.domain.dto.UpsertParameterCommand;
import cn.zqkj.platform.system.domain.dto.ExternalEndpointAuthenticationCommand;
import cn.zqkj.platform.system.domain.dto.ExternalEndpointCommand;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.model.DictionaryType;
import cn.zqkj.platform.system.domain.model.DictionaryItem;
import cn.zqkj.platform.system.domain.model.ParameterDefinition;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.domain.model.ParameterValue;
import cn.zqkj.platform.system.domain.model.ParameterValueType;
import cn.zqkj.platform.system.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.domain.model.ExternalSystem;
import cn.zqkj.platform.system.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.mapper.ConfigurationMapper;
import cn.zqkj.platform.system.service.impl.ConfigurationServiceImpl;
import cn.zqkj.platform.system.service.impl.ExternalEndpointCredentialCipher;
import cn.zqkj.platform.system.service.ManagementAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证平台注册参数、适用机构和系统字典管理边界。
 */
class ConfigurationServiceTest {

    /** 验证未在代码清单注册的参数键不能写入数据库。 */
    @Test
    void rejectsUnregisteredParameterKey() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        assertThrows(InvalidRequestException.class, () -> service.upsertParameter(
                "unknown.key", command(ParameterEnvironment.PRODUCTION, null, "1", null), actor()
        ));
        verify(mapper, never()).createParameterValue(any(), any(), any(), any());
    }

    /** 验证整数参数拒绝错误类型和超出注册范围的值。 */
    @Test
    void validatesRegisteredParameterTypeAndRange() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        ParameterDefinition definition = definition(
                "platform.retry.limit", ParameterValueType.INTEGER, false, false,
                BigDecimal.ONE, BigDecimal.TEN
        );
        ConfigurationService service = service(mapper, List.of(definition), mock(OrganizationService.class));

        assertThrows(InvalidRequestException.class, () -> service.upsertParameter(
                definition.key(), command(ParameterEnvironment.PRODUCTION, null, "not-a-number", null), actor()
        ));
        assertThrows(InvalidRequestException.class, () -> service.upsertParameter(
                definition.key(), command(ParameterEnvironment.PRODUCTION, null, "11", null), actor()
        ));
    }

    /** 验证机构级参数不能越过当前用户显式机构范围。 */
    @Test
    void rejectsOrganizationScopedParameterOutsideActorScope() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        OrganizationService organizationService = mock(OrganizationService.class);
        when(organizationService.get(20L)).thenReturn(organization(20L, "ORG002"));
        ParameterDefinition definition = definition(
                "platform.display.mode", ParameterValueType.STRING, true, false, null, null
        );
        ConfigurationService service = service(mapper, List.of(definition), organizationService);

        assertThrows(AccessDeniedException.class, () -> service.upsertParameter(
                definition.key(), command(ParameterEnvironment.PRODUCTION, 20L, "compact", null), actor()
        ));
    }

    /** 验证敏感参数值在API输出中只返回固定掩码。 */
    @Test
    void masksSensitiveParameterValue() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        ParameterDefinition definition = definition(
                "platform.masked.sample", ParameterValueType.STRING, false, true, null, null
        );
        UpsertParameterCommand command = command(ParameterEnvironment.PRODUCTION, null, "secret-value", null);
        ParameterValue saved = parameterValue(9L, definition.key(), "secret-value");
        when(mapper.findParameterValue(eq(definition.key()), any())).thenReturn(Optional.empty());
        when(mapper.createParameterValue(eq(definition.key()), eq("STRING"), any(), eq("admin"))).thenReturn(9L);
        when(mapper.findParameterValueById(9L)).thenReturn(Optional.of(saved));
        ConfigurationService service = service(mapper, List.of(definition), mock(OrganizationService.class));

        assertEquals("******", service.upsertParameter(definition.key(), command, actor()).value());
    }

    /** 验证删除参数配置时按适用范围和并发版本删除唯一记录。 */
    @Test
    void deletesParameterConfigurationByScopeAndVersion() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        ParameterDefinition definition = definition(
                "platform.display.mode", ParameterValueType.STRING, false, false, null, null
        );
        ParameterValue current = parameterValue(9L, definition.key(), "compact");
        when(mapper.findParameterValue(eq(definition.key()), any())).thenReturn(Optional.of(current));
        when(mapper.deleteParameterValue(9L, current.version())).thenReturn(1);
        ConfigurationService service = service(mapper, List.of(definition), mock(OrganizationService.class));

        service.deleteParameter(
                definition.key(),
                new DeleteParameterCommand(ParameterEnvironment.PRODUCTION, null, current.version()),
                actor()
        );

        verify(mapper).deleteParameterValue(9L, current.version());
    }

    /** 验证参数配置被他人修改后不能使用旧版本删除。 */
    @Test
    void reportsParameterDeleteConflict() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        ParameterDefinition definition = definition(
                "platform.display.mode", ParameterValueType.STRING, false, false, null, null
        );
        ParameterValue current = parameterValue(9L, definition.key(), "compact");
        when(mapper.findParameterValue(eq(definition.key()), any())).thenReturn(Optional.of(current));
        ConfigurationService service = service(mapper, List.of(definition), mock(OrganizationService.class));

        assertThrows(ResourceConflictException.class, () -> service.deleteParameter(
                definition.key(),
                new DeleteParameterCommand(ParameterEnvironment.PRODUCTION, null, current.version()),
                actor()
        ));
    }

    /** 验证重复系统字典类型代码在写入前被拒绝。 */
    @Test
    void rejectsDuplicateDictionaryTypeCode() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        when(mapper.dictionaryTypeCodeExists("DISPLAY_MODE")).thenReturn(true);
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        assertThrows(ResourceConflictException.class, () -> service.createDictionaryType(
                new CreateDictionaryTypeCommand("display_mode", "显示模式", "平台界面显示模式"), actor()
        ));
    }

    /** 验证停用字典类型不能新增字典项。 */
    @Test
    void rejectsItemCreationForDisabledDictionaryType() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        when(mapper.findDictionaryType(1L)).thenReturn(Optional.of(dictionaryType(false)));
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        assertThrows(ResourceConflictException.class, () -> service.createDictionaryItem(
                1L, new CreateDictionaryItemCommand("COMPACT", "紧凑", 10), actor()
        ));
    }

    /** 验证同一字典类型内重复项代码在写入前被拒绝。 */
    @Test
    void rejectsDuplicateDictionaryItemCode() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        when(mapper.findDictionaryType(1L)).thenReturn(Optional.of(dictionaryType(true)));
        when(mapper.dictionaryItemCodeExists(1L, "COMPACT")).thenReturn(true);
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        assertThrows(ResourceConflictException.class, () -> service.createDictionaryItem(
                1L, new CreateDictionaryItemCommand("compact", "紧凑", 10), actor()
        ));
        verify(mapper, never()).createDictionaryItem(anyLong(), any(), any());
    }

    /** 验证默认字典项查询明确要求Mapper排除停用项。 */
    @Test
    void excludesDisabledDictionaryItemsByDefault() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        when(mapper.findDictionaryType(1L)).thenReturn(Optional.of(dictionaryType(true)));
        when(mapper.findDictionaryItems(1L, false)).thenReturn(List.of());
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        assertEquals(List.of(), service.findDictionaryItems(1L, false));
        verify(mapper).findDictionaryItems(1L, false);
    }

    /** 验证字典类型并发版本不匹配时返回冲突。 */
    @Test
    void reportsDictionaryTypeConcurrentUpdateConflict() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        when(mapper.findDictionaryType(1L)).thenReturn(Optional.of(dictionaryType(true)));
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));
        UpdateDictionaryTypeCommand command = new UpdateDictionaryTypeCommand(
                "显示模式", "平台界面显示模式", true, version()
        );

        assertThrows(ResourceConflictException.class,
                () -> service.updateDictionaryType(1L, command, actor()));
        verify(mapper).updateDictionaryType(1L, command, "admin");
    }

    /** 验证包含字典项的类型不能删除，并直接说明需先处理的数量。 */
    @Test
    void rejectsDeletingDictionaryTypeWithItems() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        DictionaryType type = dictionaryType(true);
        when(mapper.findDictionaryType(1L)).thenReturn(Optional.of(type));
        when(mapper.countDictionaryItems(1L)).thenReturn(2L);
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        assertThrows(ResourceConflictException.class,
                () -> service.deleteDictionaryType(1L, type.version(), actor()));
        verify(mapper, never()).deleteDictionaryType(anyLong(), any());
    }

    /** 验证未被业务数据引用的字典项可以按并发版本删除。 */
    @Test
    void deletesUnusedDictionaryItem() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        DictionaryItem item = dictionaryItem();
        when(mapper.findDictionaryItem(9L)).thenReturn(Optional.of(item));
        when(mapper.findDictionaryType(1L)).thenReturn(Optional.of(dictionaryType(true)));
        when(mapper.countDictionaryItemReferences(9L)).thenReturn(0L);
        when(mapper.deleteDictionaryItem(9L, item.version())).thenReturn(1);
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        service.deleteDictionaryItem(9L, item.version(), actor());

        verify(mapper).deleteDictionaryItem(9L, item.version());
    }

    /** 验证已被业务数据引用的字典项只能停用，不能物理删除。 */
    @Test
    void rejectsDeletingReferencedDictionaryItem() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        DictionaryItem item = dictionaryItem();
        when(mapper.findDictionaryItem(9L)).thenReturn(Optional.of(item));
        when(mapper.findDictionaryType(1L)).thenReturn(Optional.of(dictionaryType(true)));
        when(mapper.countDictionaryItemReferences(9L)).thenReturn(3L);
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        assertThrows(ResourceConflictException.class,
                () -> service.deleteDictionaryItem(9L, item.version(), actor()));
        verify(mapper, never()).deleteDictionaryItem(anyLong(), any());
    }

    /** 验证服务地址拒绝不支持的协议、地址凭证和查询参数。 */
    @Test
    void rejectsInvalidExternalEndpointUrl() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        when(mapper.findExternalSystem(1L)).thenReturn(Optional.of(externalSystem(true)));
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        assertThrows(InvalidRequestException.class, () -> service.createExternalEndpoint(
                1L, endpointCommand("ftp://his.local/api", null), actor()
        ));
        assertThrows(InvalidRequestException.class, () -> service.createExternalEndpoint(
                1L, endpointCommand("https://user:pass@his.local/api", null), actor()
        ));
        assertThrows(InvalidRequestException.class, () -> service.createExternalEndpoint(
                1L, endpointCommand("http://his.local/api?op=PHIS_Interface", null), actor()
        ));
        verify(mapper, never()).createExternalEndpoint(anyLong(), any(), any());
    }

    /** 验证正式WSDL使用的HTTP WebService基础地址能够登记。 */
    @Test
    void acceptsHttpExternalEndpointUrl() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        when(mapper.findExternalSystem(1L)).thenReturn(Optional.of(externalSystem(true)));
        when(mapper.createExternalEndpoint(eq(1L), any(), eq("admin"))).thenReturn(9L);
        when(mapper.findExternalEndpoint(9L)).thenReturn(Optional.of(externalEndpoint()));
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        service.createExternalEndpoint(
                1L, endpointCommand("http://his.example.invalid/WebService.asmx", null), actor()
        );

        verify(mapper).createExternalEndpoint(eq(1L),
                argThat(command -> "http://his.example.invalid/WebService.asmx".equals(command.baseUrl())
                        && command.enabled()),
                eq("admin"));
    }

    /** 验证首次配置必须提供业务人员可识别的厂商编号和机构授权码。 */
    @Test
    void rejectsIncompleteEndpointAuthentication() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        when(mapper.findExternalSystem(1L)).thenReturn(Optional.of(externalSystem(true)));
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        ExternalEndpointCommand command = new ExternalEndpointCommand(
                ParameterEnvironment.TEST, null, "http://his.example.invalid/WebService.asmx",
                3000, 15000, new ExternalEndpointAuthenticationCommand("", null, null, "AUTH-008"),
                false, null
        );

        assertThrows(InvalidRequestException.class,
                () -> service.createExternalEndpoint(1L, command, actor()));
        verify(mapper, never()).createExternalEndpoint(anyLong(), any(), any());
    }

    /** 验证修改非认证字段时可以保留现有机构认证信息。 */
    @Test
    void retainsExistingCredentialReferenceWhenUpdateDoesNotRotateCredential() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        ExternalEndpoint current = externalEndpoint();
        when(mapper.findExternalEndpoint(9L)).thenReturn(Optional.of(current));
        when(mapper.updateExternalEndpoint(eq(9L), any(), eq("admin"))).thenReturn(1);
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        service.updateExternalEndpoint(9L, new ExternalEndpointCommand(
                ParameterEnvironment.TEST, null, "http://his.example.invalid/WebService.asmx", 4000, 16000,
                null, false, current.version()
        ), actor());

        verify(mapper).updateExternalEndpoint(eq(9L), argThat(command ->
                command.environment() == ParameterEnvironment.TEST
                        && command.organizationId() == null
                        && command.authentication() == null), eq("admin"));
    }

    /** 验证更换适用机构时不能沿用原机构认证信息。 */
    @Test
    void requiresNewAuthenticationWhenEndpointOrganizationChanges() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        OrganizationService organizations = mock(OrganizationService.class);
        ExternalEndpoint current = externalEndpoint();
        when(mapper.findExternalEndpoint(9L)).thenReturn(Optional.of(current));
        when(organizations.get(10L)).thenReturn(organization(10L, "ORG001"));
        ConfigurationService service = service(mapper, List.of(), organizations);

        assertThrows(InvalidRequestException.class, () -> service.updateExternalEndpoint(
                9L,
                new ExternalEndpointCommand(
                        ParameterEnvironment.TEST, 10L, "http://his.example.invalid/WebService.asmx",
                        3000, 15000, null, true, current.version()
                ),
                actor()
        ));
        verify(mapper, never()).updateExternalEndpoint(anyLong(), any(), any());
    }

    /** 验证机构级服务地址不能越过当前用户显式机构范围。 */
    @Test
    void rejectsExternalEndpointOutsideActorScope() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        OrganizationService organizations = mock(OrganizationService.class);
        when(mapper.findExternalSystem(1L)).thenReturn(Optional.of(externalSystem(true)));
        when(organizations.get(20L)).thenReturn(organization(20L, "ORG002"));
        ConfigurationService service = service(mapper, List.of(), organizations);

        assertThrows(AccessDeniedException.class, () -> service.createExternalEndpoint(
                1L, endpointCommand("https://his.example.invalid/api", 20L), actor()
        ));
    }

    /** 验证API输出只说明凭证已配置，不返回凭证引用。 */
    @Test
    void hidesExternalEndpointCredentialReference() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        when(mapper.findExternalSystem(1L)).thenReturn(Optional.of(externalSystem(true)));
        when(mapper.createExternalEndpoint(eq(1L), any(), eq("admin"))).thenReturn(9L);
        when(mapper.findExternalEndpoint(9L)).thenReturn(Optional.of(externalEndpoint()));
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        assertEquals(true, service.createExternalEndpoint(
                1L, endpointCommand("https://his.example.invalid/api", null), actor()
        ).credentialConfigured());
    }

    /** @param mapper 配置Mapper @param definitions 参数定义 @param organizationService 机构服务 @return 被测服务 */
    private ConfigurationService service(
            ConfigurationMapper mapper,
            List<ParameterDefinition> definitions,
            OrganizationService organizationService
    ) {
        ParameterDefinitionRegistry registry = mock(ParameterDefinitionRegistry.class);
        when(registry.findAll()).thenReturn(definitions);
        definitions.forEach(definition -> when(registry.find(definition.key())).thenReturn(Optional.of(definition)));
        return new ConfigurationServiceImpl(mapper, registry, organizationService,
                mock(ManagementAuditService.class), mock(ExternalEndpointCredentialCipher.class));
    }

    /** @param key 参数键 @param type 类型 @param scoped 是否机构级 @param sensitive 是否敏感 @param min 下界 @param max 上界 @return 参数定义 */
    private ParameterDefinition definition(
            String key,
            ParameterValueType type,
            boolean scoped,
            boolean sensitive,
            BigDecimal min,
            BigDecimal max
    ) {
        return new ParameterDefinition(
                key, "测试参数", type, Set.of(ParameterEnvironment.PRODUCTION), scoped, sensitive,
                type == ParameterValueType.STRING ? 100 : null, min, max, null
        );
    }

    /** @param environment 环境 @param organizationId 机构 @param value 值 @param version 并发版本 @return 写入命令 */
    private UpsertParameterCommand command(
            ParameterEnvironment environment,
            Long organizationId,
            String value,
            byte[] version
    ) {
        return new UpsertParameterCommand(environment, organizationId, value, true, version);
    }

    /** @param id 主键 @param key 参数键 @param value 参数值 @return 参数值快照 */
    private ParameterValue parameterValue(long id, String key, String value) {
        return new ParameterValue(
                id, key, ParameterValueType.STRING, ParameterEnvironment.PRODUCTION, null, null, value, true,
                LocalDateTime.now(), LocalDateTime.now(), version()
        );
    }

    /** @param enabled 启用状态 @return 字典类型快照 */
    private DictionaryType dictionaryType(boolean enabled) {
        return new DictionaryType(
                1L, "DISPLAY_MODE", "显示模式", "平台界面显示模式", enabled,
                LocalDateTime.now(), LocalDateTime.now(), version()
        );
    }

    /** @return 未被引用的字典项快照 */
    private DictionaryItem dictionaryItem() {
        return new DictionaryItem(
                9L, 1L, "COMPACT", "紧凑", 10, true,
                LocalDateTime.now(), LocalDateTime.now(), version()
        );
    }

    /** @param enabled 启用状态 @return 外部系统快照 */
    private ExternalSystem externalSystem(boolean enabled) {
        return new ExternalSystem(1L, "COUNTY_HIS", "县医院HIS", "测试用途", enabled,
                LocalDateTime.now(), LocalDateTime.now(), version());
    }

    /** @return 外部服务地址快照 */
    private ExternalEndpoint externalEndpoint() {
        return new ExternalEndpoint(9L, 1L, ParameterEnvironment.TEST, null, null,
                "https://his.example.invalid/api", 3000, 15000, "managed://database", true, false,
                LocalDateTime.now(), LocalDateTime.now(), version());
    }

    /** @param url 地址 @param organizationId 可选机构 @return 服务地址创建命令 */
    private ExternalEndpointCommand endpointCommand(String url, Long organizationId) {
        return new ExternalEndpointCommand(ParameterEnvironment.TEST, organizationId, url, 3000, 15000,
                new ExternalEndpointAuthenticationCommand("V01", null, null, "AUTH-008"), true, null);
    }

    /** @param id 机构主键 @param code 机构代码 @return 机构记录 */
    private OrganizationVO organization(long id, String code) {
        return new OrganizationVO(
                id, code, "测试机构", "HOSPITAL", null, true, null, null,
                LocalDateTime.now(), LocalDateTime.now(), version()
        );
    }

    /** @return 当前测试操作人 */
    private AccessActor actor() {
        return new AccessActor(1L, "admin", Set.of("ORG001"));
    }

    /** @return 固定长度SQL Server并发版本 */
    private byte[] version() {
        return new byte[Long.BYTES];
    }
}
