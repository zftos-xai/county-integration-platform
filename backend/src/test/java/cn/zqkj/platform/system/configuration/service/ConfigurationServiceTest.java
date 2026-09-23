package cn.zqkj.platform.system.configuration.service;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.exception.ResourceNotFoundException;
import cn.zqkj.platform.exchange.service.ExchangeRuntimeRecordService;
import cn.zqkj.platform.his.domain.endpointverification.model.PhisEndpointVerificationResult;
import cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryEntry;
import cn.zqkj.platform.his.domain.organization.model.OrganizationEntry;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.service.PhisEndpointVerificationService;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.his.service.impl.PhisEndpointVerificationServiceImpl;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import cn.zqkj.platform.system.configuration.domain.dto.CreateDictionaryItemCommand;
import cn.zqkj.platform.system.configuration.domain.dto.CreateDictionaryTypeCommand;
import cn.zqkj.platform.system.configuration.domain.dto.CreateExternalEndpointRequest;
import cn.zqkj.platform.system.configuration.domain.dto.DeleteParameterCommand;
import cn.zqkj.platform.system.configuration.domain.dto.ExternalEndpointAuthenticationCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateDictionaryTypeCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateExternalEndpointRequest;
import cn.zqkj.platform.system.configuration.domain.dto.UpsertParameterCommand;
import cn.zqkj.platform.system.configuration.domain.model.DictionaryItem;
import cn.zqkj.platform.system.configuration.domain.model.DictionaryType;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointAuthentication;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointCredential;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointVerificationStatus;
import cn.zqkj.platform.system.configuration.domain.model.ExternalSystem;
import cn.zqkj.platform.system.configuration.domain.model.ParameterDefinition;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.configuration.domain.model.ParameterValue;
import cn.zqkj.platform.system.configuration.domain.model.ParameterValueType;
import cn.zqkj.platform.system.configuration.mapper.ConfigurationMapper;
import cn.zqkj.platform.system.configuration.service.impl.ConfigurationServiceImpl;
import cn.zqkj.platform.system.configuration.service.impl.ExternalEndpointCredentialCipher;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.organization.service.OrganizationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

    /** HIS 在事务外调用，成功和失败结果均只能写回读取时的版本，冲突时回滚且不记成功审计。 */
    @Test
    void rejectsStaleEndpointVerificationResultsBeforeAudit() {
        for (boolean verified : List.of(true, false)) {
            ConfigurationMapper mapper = mock(ConfigurationMapper.class);
            var verifier = mock(PhisEndpointVerificationService.class);
            var audit = mock(ManagementAuditService.class);
            var manager = mock(PlatformTransactionManager.class);
            var transaction = mock(TransactionStatus.class);
            when(manager.getTransaction(any())).thenReturn(transaction);
            ExternalEndpoint endpoint = new ExternalEndpoint(9L, 1L, ParameterEnvironment.TEST, 10L, "ORG001",
                    "http://his.example.invalid/api", 3000, 15000, "managed://database", true, false,
                    null, null, version());
            when(mapper.findExternalEndpoint(9L, List.of("ORG001"))).thenReturn(Optional.of(endpoint));
            when(mapper.findExternalSystem(1L)).thenReturn(Optional.of(new ExternalSystem(
                    1L, "PRIMARY_HIS", "基层HIS", null, true, null, null, version())));
            when(verifier.verify(eq(9L), eq(10L), eq(ParameterEnvironment.TEST), any(), eq("ORG001"), any()))
                    .thenAnswer(invocation -> {
                        Mockito.verifyNoInteractions(manager);
                        return verified
                                ? PhisEndpointVerificationResult
                                        .verified("HIS-ORG", "测试机构")
                                : PhisEndpointVerificationResult
                                        .rejected("未找到唯一机构");
                    });
            ConfigurationService service = new ConfigurationServiceImpl(mapper, mock(ParameterDefinitionRegistry.class),
                    mock(OrganizationService.class), audit, mock(ExternalEndpointCredentialCipher.class), verifier,
                    new TransactionTemplate(manager));

            assertThrows(ResourceConflictException.class, () -> service.verifyExternalEndpoint(9L, actor()));

            if (verified) {
                verify(mapper).markExternalEndpointVerified(9L, "HIS-ORG", "测试机构", endpoint.version(), "admin");
            } else {
                verify(mapper).markExternalEndpointVerificationFailed(
                        9L, "FAILED", "未找到唯一机构", endpoint.version(), "admin");
            }
            verify(manager).rollback(transaction);
            verify(manager, never()).commit(any());
            Mockito.verifyNoInteractions(audit);
        }
    }

    private TransactionTemplate transactions() {
        return new TransactionTemplate(
                mock(PlatformTransactionManager.class));
    }


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
        when(organizationService.getVisible(20L, List.of("ORG001")))
                .thenThrow(new ResourceNotFoundException("机构不可见"));
        ParameterDefinition definition = definition(
                "platform.display.mode", ParameterValueType.STRING, true, false, null, null
        );
        ConfigurationService service = service(mapper, List.of(definition), organizationService);

        assertThrows(ResourceNotFoundException.class, () -> service.upsertParameter(
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
        when(mapper.findParameterValue(eq(definition.key()), any(), any())).thenReturn(Optional.empty());
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
        when(mapper.findParameterValue(eq(definition.key()), any(), any())).thenReturn(Optional.of(current));
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
        when(mapper.findParameterValue(eq(definition.key()), any(), any())).thenReturn(Optional.of(current));
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

    /** 验证ASMX操作页HTTP地址能够登记并完整保留供管理端回显。 */
    @Test
    void acceptsHttpExternalEndpointUrl() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        when(mapper.findExternalSystem(1L)).thenReturn(Optional.of(externalSystem(true)));
        when(mapper.createExternalEndpoint(eq(1L), any(), eq("admin"))).thenReturn(9L);
        when(mapper.findExternalEndpoint(9L, List.of("ORG001"))).thenReturn(Optional.of(externalEndpoint()));
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        service.createExternalEndpoint(
                1L, endpointCommand(
                        "http://his.example.invalid/WebService.asmx?op=PHIS_Interface", null), actor()
        );

        verify(mapper).createExternalEndpoint(eq(1L),
                argThat(command -> "http://his.example.invalid/WebService.asmx?op=PHIS_Interface"
                        .equals(command.normalizedBaseUrl())),
                eq("admin"));
    }

    /** 验证首次配置必须提供业务人员可识别的厂商编号和机构授权码。 */
    @Test
    void rejectsIncompleteEndpointAuthentication() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        when(mapper.findExternalSystem(1L)).thenReturn(Optional.of(externalSystem(true)));
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        CreateExternalEndpointRequest command = new CreateExternalEndpointRequest(
                ParameterEnvironment.TEST, null, "http://his.example.invalid/WebService.asmx",
                3000, 15000, new ExternalEndpointAuthenticationCommand("", null, null, "AUTH-008"),
                false
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
        when(mapper.findExternalEndpoint(9L, List.of("ORG001"))).thenReturn(Optional.of(current));
        when(mapper.findExternalSystem(1L)).thenReturn(Optional.of(externalSystem(true)));
        when(mapper.updateExternalEndpoint(eq(9L), any(), eq("admin"))).thenReturn(1);
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        service.updateExternalEndpoint(9L, new UpdateExternalEndpointRequest(
                ParameterEnvironment.TEST, null, "http://his.example.invalid/WebService.asmx", 4000, 16000,
                null, false, current.version()
        ), actor());

        verify(mapper).updateExternalEndpoint(eq(9L), argThat(command ->
                command.environment() == ParameterEnvironment.TEST
                        && command.organizationId() == null
                        && command.authentication() == null), eq("admin"));
    }

    /** 验证100-008只在返回唯一来源机构后才允许基层HIS配置进入可同步状态。 */
    @Test
    void verifiesPrimaryHisEndpointWithExactlyOneOrganization() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        PhisService phisService = mock(PhisService.class);
        ExternalEndpoint current = new ExternalEndpoint(
                9L, 1L, ParameterEnvironment.TEST, 10L, "ORG001",
                "http://his.example.invalid/WebService.asmx", 3000, 15000, "managed://database",
                true, false, LocalDateTime.now(), LocalDateTime.now(), version(), "HIS测试机构", null, null,
                ExternalEndpointVerificationStatus.NOT_VERIFIED, null, null);
        ExternalEndpoint verified = new ExternalEndpoint(
                9L, 1L, ParameterEnvironment.TEST, 10L, "ORG001",
                "http://his.example.invalid/WebService.asmx", 3000, 15000, "managed://database",
                true, true, LocalDateTime.now(), LocalDateTime.now(), version(), "HIS测试机构", "HIS-ORG-001",
                "HIS测试机构", ExternalEndpointVerificationStatus.VERIFIED,
                LocalDateTime.now(), null);
        when(mapper.findExternalEndpoint(9L, List.of("ORG001"))).thenReturn(Optional.of(current), Optional.of(verified));
        when(mapper.findExternalSystem(1L)).thenReturn(Optional.of(new ExternalSystem(
                1L, "PRIMARY_HIS", "基层HIS", "测试用途", true,
                LocalDateTime.now(), LocalDateTime.now(), version())));
        when(phisService.verifyOrganizationConfiguration(eq(10L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(true, "1", List.of(new OrganizationEntry(
                        "HIS-ORG-001", "HIS测试机构", null, null, null, null)), null));
        when(phisService.verifyHospitalDirectoryCapability(eq(10L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(true, "1", List.<HospitalDirectoryEntry>of(), null));
        when(mapper.markExternalEndpointVerified(eq(9L), eq("HIS-ORG-001"), eq("HIS测试机构"), any(), eq("admin")))
                .thenReturn(1);
        ConfigurationService service = new ConfigurationServiceImpl(mapper, mock(ParameterDefinitionRegistry.class),
                mock(OrganizationService.class), mock(ManagementAuditService.class),
                mock(ExternalEndpointCredentialCipher.class), new PhisEndpointVerificationServiceImpl(phisService,
                mock(ExchangeRuntimeRecordService.class)), transactions());

        var result = service.verifyExternalEndpoint(9L, actor());

        assertEquals("VERIFIED", result.verificationStatus());
        assertEquals("HIS-ORG-001", result.sourceOrganizationId());
        verify(phisService).verifyHospitalDirectoryCapability(eq(10L), eq(ParameterEnvironment.TEST),
                argThat(query -> "ORG001".equals(query.sourceOrganizationCode())));
        verify(mapper).markExternalEndpointVerified(9L, "HIS-ORG-001", "HIS测试机构", current.version(), "admin");
    }

    /** 验证100-008成功但100-003被拒绝时，接口不能被错误标记为可同步。 */
    @Test
    void rejectsPrimaryHisEndpointWithoutHospitalDirectoryPermission() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        PhisService phisService = mock(PhisService.class);
        ManagementAuditService auditService = mock(ManagementAuditService.class);
        ExternalEndpoint current = new ExternalEndpoint(
                9L, 1L, ParameterEnvironment.TEST, 10L, "ORG001",
                "http://his.example.invalid/WebService.asmx", 3000, 15000, "managed://database",
                true, false, LocalDateTime.now(), LocalDateTime.now(), version(), "HIS测试机构", null, null,
                ExternalEndpointVerificationStatus.NOT_VERIFIED, null, null);
        ExternalEndpoint failed = new ExternalEndpoint(
                9L, 1L, ParameterEnvironment.TEST, 10L, "ORG001",
                "http://his.example.invalid/WebService.asmx", 3000, 15000, "managed://database",
                true, false, LocalDateTime.now(), LocalDateTime.now(), version(), "HIS测试机构", null, null,
                ExternalEndpointVerificationStatus.FAILED,
                LocalDateTime.now(), "HIS未授予100-003权限");
        when(mapper.findExternalEndpoint(9L, List.of("ORG001"))).thenReturn(Optional.of(current), Optional.of(failed));
        when(mapper.findExternalSystem(1L)).thenReturn(Optional.of(new ExternalSystem(
                1L, "PRIMARY_HIS", "基层HIS", "测试用途", true,
                LocalDateTime.now(), LocalDateTime.now(), version())));
        when(phisService.verifyOrganizationConfiguration(eq(10L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(true, "1", List.of(new OrganizationEntry(
                        "HIS-ORG-001", "HIS测试机构", null, null, null, null)), null));
        when(phisService.verifyHospitalDirectoryCapability(eq(10L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(false, "0", null, "HIS未授予100-003权限"));
        when(mapper.markExternalEndpointVerificationFailed(eq(9L), eq("FAILED"),
                eq("HIS明确拒绝了100-003医院综合目录查询"), any(), eq("admin"))).thenReturn(1);
        ConfigurationService service = new ConfigurationServiceImpl(mapper, mock(ParameterDefinitionRegistry.class),
                mock(OrganizationService.class), auditService,
                mock(ExternalEndpointCredentialCipher.class), new PhisEndpointVerificationServiceImpl(phisService,
                mock(ExchangeRuntimeRecordService.class)), transactions());

        var result = service.verifyExternalEndpoint(9L, actor());

        assertEquals("FAILED", result.verificationStatus());
        verify(mapper, never()).markExternalEndpointVerified(anyLong(), any(), any(), any(), any());
        verify(auditService).append(argThat(command -> "FAILURE".equals(command.resultCode())
                && "PRIMARY_HIS_ENDPOINT_VERIFICATION_FAILED".equals(command.actionCode())
                && !command.changeSummary().contains("未确认唯一来源机构")));
    }

    /** 验证来源机构编码未变时，允许按100-008最新返回值更新机构名称。 */
    @Test
    void updatesReturnedOrganizationNameWhenSourceIdIsStable() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        PhisService phisService = mock(PhisService.class);
        ExternalEndpoint current = new ExternalEndpoint(
                9L, 1L, ParameterEnvironment.TEST, 10L, "ORG001",
                "http://his.example.invalid/WebService.asmx", 3000, 15000, "managed://database",
                true, false, LocalDateTime.now(), LocalDateTime.now(), version(), "目标卫生院", "HIS-ORG-001",
                "目标卫生院旧名称",
                ExternalEndpointVerificationStatus.NOT_VERIFIED, null, null);
        ExternalEndpoint verified = new ExternalEndpoint(
                9L, 1L, ParameterEnvironment.TEST, 10L, "ORG001",
                "http://his.example.invalid/WebService.asmx", 3000, 15000, "managed://database",
                true, true, LocalDateTime.now(), LocalDateTime.now(), version(), "目标卫生院", "HIS-ORG-001",
                "目标卫生院新名称", ExternalEndpointVerificationStatus.VERIFIED,
                LocalDateTime.now(), null);
        when(mapper.findExternalEndpoint(9L, List.of("ORG001"))).thenReturn(Optional.of(current), Optional.of(verified));
        when(mapper.findExternalSystem(1L)).thenReturn(Optional.of(new ExternalSystem(
                1L, "PRIMARY_HIS", "基层HIS", "测试用途", true,
                LocalDateTime.now(), LocalDateTime.now(), version())));
        when(phisService.verifyOrganizationConfiguration(eq(10L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(true, "1", List.of(new OrganizationEntry(
                        "HIS-ORG-001", "目标卫生院新名称", null, null, null, null)), null));
        when(phisService.verifyHospitalDirectoryCapability(eq(10L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(true, "1", List.<HospitalDirectoryEntry>of(), null));
        when(mapper.markExternalEndpointVerified(eq(9L), eq("HIS-ORG-001"),
                eq("目标卫生院新名称"), any(), eq("admin")))
                .thenReturn(1);
        ConfigurationService service = new ConfigurationServiceImpl(mapper, mock(ParameterDefinitionRegistry.class),
                mock(OrganizationService.class), mock(ManagementAuditService.class),
                mock(ExternalEndpointCredentialCipher.class), new PhisEndpointVerificationServiceImpl(phisService,
                mock(ExchangeRuntimeRecordService.class)), transactions());

        var result = service.verifyExternalEndpoint(9L, actor());

        assertEquals("VERIFIED", result.verificationStatus());
        assertEquals("目标卫生院新名称", result.sourceOrganizationName());
        verify(mapper).markExternalEndpointVerified(9L, "HIS-ORG-001", "目标卫生院新名称", current.version(), "admin");
    }

    /** 验证十六进制形式的机构编码只有大小写变化时仍视为同一机构。 */
    @Test
    void acceptsStableSourceOrganizationIdWithDifferentLetterCase() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        PhisService phisService = mock(PhisService.class);
        ExternalEndpoint current = new ExternalEndpoint(
                9L, 1L, ParameterEnvironment.TEST, 10L, "ORG001",
                "http://his.example.invalid/WebService.asmx", 3000, 15000, "managed://database",
                true, false, LocalDateTime.now(), LocalDateTime.now(), version(), "目标卫生院", "03cdd57ce12f4720bb73ab71a944a036",
                "目标卫生院旧名称",
                ExternalEndpointVerificationStatus.NOT_VERIFIED, null, null);
        ExternalEndpoint verified = new ExternalEndpoint(
                9L, 1L, ParameterEnvironment.TEST, 10L, "ORG001",
                "http://his.example.invalid/WebService.asmx", 3000, 15000, "managed://database",
                true, true, LocalDateTime.now(), LocalDateTime.now(), version(), "目标卫生院", "03CDD57CE12F4720BB73AB71A944A036",
                "目标卫生院新名称", ExternalEndpointVerificationStatus.VERIFIED,
                LocalDateTime.now(), null);
        when(mapper.findExternalEndpoint(9L, List.of("ORG001"))).thenReturn(Optional.of(current), Optional.of(verified));
        when(mapper.findExternalSystem(1L)).thenReturn(Optional.of(new ExternalSystem(
                1L, "PRIMARY_HIS", "基层HIS", "测试用途", true,
                LocalDateTime.now(), LocalDateTime.now(), version())));
        when(phisService.verifyOrganizationConfiguration(eq(10L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(true, "1", List.of(new OrganizationEntry(
                        "03CDD57CE12F4720BB73AB71A944A036", "目标卫生院新名称", null, null, null, null)), null));
        when(phisService.verifyHospitalDirectoryCapability(eq(10L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(true, "1", List.<HospitalDirectoryEntry>of(), null));
        when(mapper.markExternalEndpointVerified(eq(9L), eq("03CDD57CE12F4720BB73AB71A944A036"),
                eq("目标卫生院新名称"), any(), eq("admin")))
                .thenReturn(1);
        ConfigurationService service = new ConfigurationServiceImpl(mapper, mock(ParameterDefinitionRegistry.class),
                mock(OrganizationService.class), mock(ManagementAuditService.class),
                mock(ExternalEndpointCredentialCipher.class), new PhisEndpointVerificationServiceImpl(phisService,
                mock(ExchangeRuntimeRecordService.class)), transactions());

        var result = service.verifyExternalEndpoint(9L, actor());

        assertEquals("VERIFIED", result.verificationStatus());
        assertEquals("03CDD57CE12F4720BB73AB71A944A036", result.sourceOrganizationId());
        verify(phisService).verifyHospitalDirectoryCapability(eq(10L), eq(ParameterEnvironment.TEST), any());
        verify(mapper).markExternalEndpointVerified(9L, "03CDD57CE12F4720BB73AB71A944A036",
                "目标卫生院新名称", current.version(), "admin");
    }

    /** 验证100-008返回的机构编码变化时阻断配置，且不验证100-003。 */
    @Test
    void rejectsChangedSourceOrganizationId() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        PhisService phisService = mock(PhisService.class);
        ExternalEndpoint current = new ExternalEndpoint(
                9L, 1L, ParameterEnvironment.TEST, 10L, "ORG001",
                "http://his.example.invalid/WebService.asmx", 3000, 15000, "managed://database",
                true, false, LocalDateTime.now(), LocalDateTime.now(), version(), "目标卫生院", "OLD-ID",
                "目标卫生院", ExternalEndpointVerificationStatus.NOT_VERIFIED,
                null, null);
        ExternalEndpoint failed = new ExternalEndpoint(
                9L, 1L, ParameterEnvironment.TEST, 10L, "ORG001",
                "http://his.example.invalid/WebService.asmx", 3000, 15000, "managed://database",
                true, false, LocalDateTime.now(), LocalDateTime.now(), version(), "目标卫生院", "OLD-ID",
                "目标卫生院", ExternalEndpointVerificationStatus.FAILED,
                LocalDateTime.now(), "100-008返回的机构编码与上次确认结果不一致，请核对HIS机构资料");
        when(mapper.findExternalEndpoint(9L, List.of("ORG001"))).thenReturn(Optional.of(current), Optional.of(failed));
        when(mapper.findExternalSystem(1L)).thenReturn(Optional.of(new ExternalSystem(
                1L, "PRIMARY_HIS", "基层HIS", "测试用途", true,
                LocalDateTime.now(), LocalDateTime.now(), version())));
        when(phisService.verifyOrganizationConfiguration(eq(10L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(true, "1", List.of(new OrganizationEntry(
                        "NEW-ID", "目标卫生院新名称", null, null, null, null)), null));
        when(mapper.markExternalEndpointVerificationFailed(eq(9L), eq("FAILED"),
                eq("100-008返回的机构编码与上次确认结果不一致，请核对HIS机构资料"), any(), eq("admin")))
                .thenReturn(1);
        ConfigurationService service = new ConfigurationServiceImpl(mapper, mock(ParameterDefinitionRegistry.class),
                mock(OrganizationService.class), mock(ManagementAuditService.class),
                mock(ExternalEndpointCredentialCipher.class), new PhisEndpointVerificationServiceImpl(phisService,
                mock(ExchangeRuntimeRecordService.class)), transactions());

        var result = service.verifyExternalEndpoint(9L, actor());

        assertEquals("FAILED", result.verificationStatus());
        verify(phisService, never()).verifyHospitalDirectoryCapability(anyLong(), any(), any());
        verify(mapper, never()).markExternalEndpointVerified(anyLong(), any(), any(), any(), any());
    }

    /**
     * 外部HIS校验不能占用数据库长事务。
     *
     * <p>结果写回和审计由独立短事务保护，不由整个HTTP调用持有事务。</p>
     */
    @Test
    void verifiesPrimaryHisEndpointOutsideTransaction() throws NoSuchMethodException {
        Assertions.assertFalse(ConfigurationServiceImpl.class
                .getMethod("verifyExternalEndpoint", long.class, AccessActor.class)
                .isAnnotationPresent(Transactional.class));
    }

    /** 验证更换适用机构时不能沿用原机构认证信息。 */
    @Test
    void requiresNewAuthenticationWhenEndpointOrganizationChanges() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        OrganizationService organizations = mock(OrganizationService.class);
        ExternalEndpoint current = externalEndpoint();
        when(mapper.findExternalEndpoint(9L, List.of("ORG001"))).thenReturn(Optional.of(current));
        when(organizations.get(10L)).thenReturn(organization(10L, "ORG001"));
        ConfigurationService service = service(mapper, List.of(), organizations);

        assertThrows(InvalidRequestException.class, () -> service.updateExternalEndpoint(
                9L,
                new UpdateExternalEndpointRequest(
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
        when(organizations.getVisible(20L, List.of("ORG001")))
                .thenThrow(new ResourceNotFoundException("机构不可见"));
        ConfigurationService service = service(mapper, List.of(), organizations);

        assertThrows(ResourceNotFoundException.class, () -> service.createExternalEndpoint(
                1L, endpointCommand("https://his.example.invalid/api", 20L), actor()
        ));
        verify(mapper, never()).createExternalEndpoint(anyLong(), any(), any());
    }

    /** 验证API输出只说明凭证已配置，不返回凭证引用。 */
    @Test
    void hidesExternalEndpointCredentialReference() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        when(mapper.findExternalSystem(1L)).thenReturn(Optional.of(externalSystem(true)));
        when(mapper.createExternalEndpoint(eq(1L), any(), eq("admin"))).thenReturn(9L);
        when(mapper.findExternalEndpoint(9L, List.of("ORG001"))).thenReturn(Optional.of(externalEndpoint()));
        ConfigurationService service = service(mapper, List.of(), mock(OrganizationService.class));

        assertEquals(true, service.createExternalEndpoint(
                1L, endpointCommand("https://his.example.invalid/api", null), actor()
        ).credentialConfigured());
    }

    /** 验证管理员只能在机构范围内按需解密查看接入信息。 */
    @Test
    void revealsExternalEndpointAuthenticationWithinActorScope() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        ExternalEndpointCredential credential = new ExternalEndpointCredential(9L, new byte[]{1}, new byte[]{2}, 1);
        ExternalEndpointCredentialCipher cipher = mock(ExternalEndpointCredentialCipher.class);
        ManagementAuditService auditService = mock(ManagementAuditService.class);
        when(mapper.findExternalEndpoint(9L, List.of("ORG001"))).thenReturn(Optional.of(externalEndpoint()));
        when(mapper.findExternalEndpointCredential(9L)).thenReturn(Optional.of(credential));
        when(cipher.decrypt(credential)).thenReturn(new ExternalEndpointAuthentication(
                "V01", "his-user", "his-password", "AUTH-008"
        ));
        ParameterDefinitionRegistry registry = mock(ParameterDefinitionRegistry.class);
        ConfigurationService service = new ConfigurationServiceImpl(
                mapper, registry, mock(OrganizationService.class), auditService, cipher,
                mock(PhisEndpointVerificationService.class), transactions()
        );

        var result = service.findExternalEndpointAuthentication(9L, actor());

        assertEquals("V01", result.vendorCode());
        assertEquals("his-user", result.username());
        assertEquals("his-password", result.password());
        assertEquals("AUTH-008", result.authorizationCode());
        verify(auditService).append(argThat(command ->
                "EXTERNAL_ENDPOINT_AUTHENTICATION_VIEWED".equals(command.actionCode())
                        && !command.changeSummary().contains("AUTH-008")
                        && !command.changeSummary().contains("his-password")
        ));
    }

    /**
     * 创建被测服务并注入测试替身。
     *
     * @param mapper 配置Mapper
     * @param definitions 参数定义
     * @param organizationService 机构服务
     * @return 被测服务
     */
    private ConfigurationService service(
            ConfigurationMapper mapper,
            List<ParameterDefinition> definitions,
            OrganizationService organizationService
    ) {
        ParameterDefinitionRegistry registry = mock(ParameterDefinitionRegistry.class);
        when(registry.findAll()).thenReturn(definitions);
        definitions.forEach(definition -> when(registry.find(definition.key())).thenReturn(Optional.of(definition)));
        return new ConfigurationServiceImpl(mapper, registry, organizationService,
                mock(ManagementAuditService.class), mock(ExternalEndpointCredentialCipher.class),
                mock(PhisEndpointVerificationService.class), transactions());
    }

    /**
     * 创建代码注册参数定义测试数据。
     *
     * @param key 参数键
     * @param type 类型
     * @param scoped 是否机构级
     * @param sensitive 是否敏感
     * @param min 下界
     * @param max 上界
     * @return 参数定义
     */
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

    /**
     * 创建参数写入命令测试数据。
     *
     * @param environment 环境
     * @param organizationId 机构
     * @param value 值
     * @param version 并发版本
     * @return 写入命令
     */
    private UpsertParameterCommand command(
            ParameterEnvironment environment,
            Long organizationId,
            String value,
            byte[] version
    ) {
        return new UpsertParameterCommand(environment, organizationId, value, true, version);
    }

    /**
     * 创建已持久化参数值测试快照。
     *
     * @param id 主键
     * @param key 参数键
     * @param value 参数值
     * @return 参数值快照
     */
    private ParameterValue parameterValue(long id, String key, String value) {
        return new ParameterValue(
                id, key, ParameterValueType.STRING, ParameterEnvironment.PRODUCTION, null, null, value, true,
                LocalDateTime.now(), LocalDateTime.now(), version()
        );
    }

    /**
     * 创建系统字典类型测试快照。
     *
     * @param enabled 启用状态
     * @return 字典类型快照
     */
    private DictionaryType dictionaryType(boolean enabled) {
        return new DictionaryType(
                1L, "DISPLAY_MODE", "显示模式", "平台界面显示模式", enabled,
                LocalDateTime.now(), LocalDateTime.now(), version()
        );
    }

    /**
     * 创建系统字典项测试快照。
     *
     * @return 未被引用的字典项快照
     */
    private DictionaryItem dictionaryItem() {
        return new DictionaryItem(
                9L, 1L, "COMPACT", "紧凑", 10, true,
                LocalDateTime.now(), LocalDateTime.now(), version()
        );
    }

    /**
     * 创建外部系统测试快照。
     *
     * @param enabled 启用状态
     * @return 外部系统快照
     */
    private ExternalSystem externalSystem(boolean enabled) {
        return new ExternalSystem(1L, "COUNTY_HIS", "县医院HIS", "测试用途", enabled,
                LocalDateTime.now(), LocalDateTime.now(), version());
    }

    /**
     * 创建外部系统端点测试快照。
     *
     * @return 外部服务地址快照
     */
    private ExternalEndpoint externalEndpoint() {
        return new ExternalEndpoint(9L, 1L, ParameterEnvironment.TEST, null, null,
                "https://his.example.invalid/api", 3000, 15000, "managed://database", true, false,
                LocalDateTime.now(), LocalDateTime.now(), version());
    }

    /**
     * 创建端点保存命令测试数据。
     *
     * @param url 地址
     * @param organizationId 可选机构
     * @return 服务地址创建命令
     */
    private CreateExternalEndpointRequest endpointCommand(String url, Long organizationId) {
        return new CreateExternalEndpointRequest(ParameterEnvironment.TEST, organizationId, url, 3000, 15000,
                new ExternalEndpointAuthenticationCommand("V01", null, null, "AUTH-008"), true);
    }

    /**
     * 创建机构测试快照。
     *
     * @param id 机构主键
     * @param code 机构代码
     * @return 机构记录
     */
    private OrganizationVO organization(long id, String code) {
        return new OrganizationVO(
                id, code, "测试机构", "HOSPITAL", null, true, null, null,
                LocalDateTime.now(), LocalDateTime.now(), version()
        );
    }

    /**
     * 创建具有测试机构范围的服务层操作人。
     *
     * @return 当前测试操作人
     */
    private AccessActor actor() {
        return new AccessActor(1L, "admin", Set.of("ORG001"));
    }

    /**
     * 创建确定性的8字节SQL Server行版本。
     *
     * @return 固定长度SQL Server并发版本
     */
    private byte[] version() {
        return new byte[Long.BYTES];
    }
}
