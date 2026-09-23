package cn.zqkj.platform.masterdata.service.hospitaldirectory;

import cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper;
import cn.zqkj.platform.exchange.domain.model.ExchangeRuntimeRecord;
import cn.zqkj.platform.exchange.service.ExchangeRuntimeRecordService;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectorySyncResultVO;
import java.util.ArrayList;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCounts;
import cn.zqkj.platform.his.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryEntry;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataScopeType;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataSyncMode;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryType;
import cn.zqkj.platform.masterdata.mapper.hospitaldirectory.HospitalDirectorySyncMapper;
import cn.zqkj.platform.masterdata.service.hospitaldirectory.impl.HospitalDirectorySyncServiceImpl;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证100-003按目录类型独立对账，不再使用整批发布门槛。 */
class HospitalDirectorySyncServiceTest {

    /** 不可信来源错误只能归类，地址、凭证和多行正文不得进入分项或总摘要。 */
    @Test
    void neverPersistsRawUpstreamFailureText() {
        Fixture fixture = fixture();
        when(fixture.phisService().queryHospitalDirectory(eq(10L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(false, "0", null,
                        "http://internal.invalid password=SYNTHETIC-SECRET\npatient body"));
        fixture.service().synchronize(snapshot(), 1L, "admin");
        verify(fixture.mapper(), times(4)).saveDirectoryResult(eq(25L),
                org.mockito.ArgumentMatchers.argThat(result -> result.failureSummary().equals(
                        "基层HIS拒绝目录查询，请核查来源系统配置与交易记录")));
        verify(fixture.mapper()).finishCompleted(eq(25L), eq("COMPLETED_WITH_ERRORS"), any(), any(),
                org.mockito.ArgumentMatchers.argThat(summary -> !summary.contains("SYNTHETIC")
                        && !summary.contains("internal.invalid") && !summary.contains("patient")), eq("admin"));
        verify(fixture.exchangeRecords(), times(4)).record(org.mockito.ArgumentMatchers.argThat(record ->
                !record.resultMessage().contains("SYNTHETIC")
                        && !record.requestSummary().contains("SYNTHETIC")
                        && !record.requestSummary().contains("internal.invalid")));
    }

    /** 当前目录写入与对应分项事实必须先于同一次事务提交。 */
    @Test
    void savesTypeResultBeforeCommittingDirectoryChanges() {
        Fixture fixture = fixture();
        when(fixture.phisService().queryHospitalDirectory(eq(10L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(success(entry("D1", "目录")));
        fixture.service().synchronize(snapshot(), 1L, "admin");
        var order = org.mockito.Mockito.inOrder(fixture.mapper(), fixture.transactions());
        order.verify(fixture.mapper()).insertDirectoryDirect(eq(25L), eq(10L), any());
        order.verify(fixture.mapper()).saveDirectoryResult(eq(25L), any());
        order.verify(fixture.transactions()).commit(any());
    }

    /** 依赖类型失败时保留既有关系，不能把本次未能验证的关系当作已删除。 */
    @Test
    void preservesRelationsWhenDependencyFetchFails() {
        Fixture fixture = fixture();
        when(fixture.phisService().queryHospitalDirectory(eq(10L), eq(ParameterEnvironment.TEST), any()))
                .thenAnswer(invocation -> {
                    var type = invocation.getArgument(2, HospitalDirectoryQuery.class).directoryType();
                    return type == cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryType.DEPARTMENT
                            ? new PhisResponse<>(false, "0", null, "拒绝")
                            : success(entry(type.name(), type.name()));
                });
        fixture.service().synchronize(snapshot(), 1L, "admin");
        verify(fixture.mapper(), never()).deleteRelationsForType(10L, HospitalDirectoryType.WARD);
        verify(fixture.mapper()).markMissingDirectoryInvalid(25L, 10L, HospitalDirectoryType.WARD);
    }

    /** 批次已被收尾后，旧执行者不得再写目录、分项结果或批次汇总。 */
    @Test
    void fencesStaleWorkerBeforeAnyLocalWrite() {
        Fixture fixture = fixture();
        when(fixture.batchMapper().lockExecution(anyLong(), any())).thenReturn(false);
        when(fixture.phisService().queryHospitalDirectory(eq(10L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(success(entry("D1", "科室")));
        org.junit.jupiter.api.Assertions.assertThrows(cn.zqkj.platform.common.exception.ResourceConflictException.class,
                () -> fixture.service().synchronize(snapshot(), 1L, "admin"));
        org.mockito.Mockito.verifyNoInteractions(fixture.mapper(), fixture.auditService());
    }

    /** 中断收尾只汇总已提交事实；缺少分项必须标为未知，且不再调用 HIS。 */
    @Test
    void recoversInterruptedBatchWithoutInventingSuccessOrCallingHis() {
        Fixture fixture = fixture();
        fixture.service().completeRecordedResults(snapshot(), 1L, "admin");
        verify(fixture.mapper()).finishCompleted(eq(25L), eq("COMPLETED_WITH_UNKNOWN"), any(),
                eq("HIS_PARTIAL_RESULT_UNKNOWN"), any(), eq("admin"));
        org.mockito.Mockito.verifyNoInteractions(fixture.phisService());
        verify(fixture.mapper(), never()).saveDirectoryResult(anyLong(), any());
    }

    /** 验证一个目录类型失败时，其他成功类型仍直接对账，失败类型不进入失效标记。 */
    @Test
    void synchronizesSuccessfulTypesWhenAnotherTypeIsRejectedByHis() {
        Fixture fixture = fixture();
        when(fixture.phisService().queryHospitalDirectory(eq(10L), eq(ParameterEnvironment.TEST), any(HospitalDirectoryQuery.class)))
                .thenAnswer(invocation -> {
                    cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryType type = invocation.getArgument(2, HospitalDirectoryQuery.class).directoryType();
                    return type == cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryType.DOCTOR
                            ? new PhisResponse<>(false, "0", null, "无权访问")
                            : success(entry(type.name(), typeLabel(HospitalDirectoryType.valueOf(type.name()))));
                });

        fixture.service().synchronize(snapshot(), 1L, "admin");

        verify(fixture.mapper(), times(3)).markMissingDirectoryInvalid(eq(25L), eq(10L), any(HospitalDirectoryType.class));
        verify(fixture.mapper(), never()).markMissingDirectoryInvalid(eq(25L), eq(10L), eq(HospitalDirectoryType.DOCTOR));
        verify(fixture.mapper()).finishCompleted(eq(25L), eq("COMPLETED_WITH_ERRORS"), eq(new MasterDataBatchCounts(null, 3L, 0L, 0L, 0L, 3L, 0L, 0L, 0L, 3L)), eq("HIS_PARTIAL_FAILURE"), contains("医生"), eq("admin"));
        verify(fixture.mapper(), times(4)).saveDirectoryResult(eq(25L), any());
        verify(fixture.auditService()).append(any());
    }

    /** 验证一类数据字段不合法时，不会把该类型HIS未返回的旧记录误标为无效。 */
    @Test
    void doesNotInvalidateTypeWhenAutomaticValidationFails() {
        Fixture fixture = fixture();
        when(fixture.phisService().queryHospitalDirectory(eq(10L), eq(ParameterEnvironment.TEST), any(HospitalDirectoryQuery.class)))
                .thenAnswer(invocation -> {
                    cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryType type = invocation.getArgument(2, HospitalDirectoryQuery.class).directoryType();
                    return type == cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryType.WARD
                            ? success(entry("", "病区"))
                            : success(entry(type.name(), typeLabel(HospitalDirectoryType.valueOf(type.name()))));
                });

        fixture.service().synchronize(snapshot(), 1L, "admin");

        verify(fixture.mapper(), never()).markMissingDirectoryInvalid(eq(25L), eq(10L), eq(HospitalDirectoryType.WARD));
        verify(fixture.mapper()).finishCompleted(eq(25L), eq("COMPLETED_WITH_ERRORS"), eq(new MasterDataBatchCounts(null, 4L, 0L, 1L, 0L, 3L, 0L, 0L, 0L, 3L)), eq("HIS_PARTIAL_FAILURE"), contains("病区"), eq("admin"));
    }

    /** 验证单个类型通信结果未知时，不阻断其他类型，并准确记录部分结果未知。 */
    @Test
    void preservesOtherTypesWhenOneTypeResultIsUnknown() {
        Fixture fixture = fixture();
        when(fixture.phisService().queryHospitalDirectory(eq(10L), eq(ParameterEnvironment.TEST), any(HospitalDirectoryQuery.class)))
                .thenAnswer(invocation -> {
                    cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryType type = invocation.getArgument(2, HospitalDirectoryQuery.class).directoryType();
                    if (type == cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryType.BED) {
                        throw new PhisCommunicationException("超时");
                    }
                    return success(entry(type.name(), typeLabel(HospitalDirectoryType.valueOf(type.name()))));
                });

        fixture.service().synchronize(snapshot(), 1L, "admin");

        verify(fixture.mapper()).finishCompleted(eq(25L), eq("COMPLETED_WITH_UNKNOWN"), eq(new MasterDataBatchCounts(null, 3L, 0L, 0L, 0L, 3L, 0L, 0L, 0L, 3L)), eq("HIS_PARTIAL_RESULT_UNKNOWN"), contains("床位"), eq("admin"));
    }


    /**
     * 创建最小且不含敏感信息的医院目录来源条目。
     *
     * @param code 来源编码
     * @param name 来源名称
     * @return 最小非敏感来源记录
     */
    private HospitalDirectoryEntry entry(String code, String name) {
        return new HospitalDirectoryEntry(code, name, null, null, null, null, null, null, "HIS-ORG-001",
                null, null, null, null, null, null, null, null, null, List.of(), null, null, null, null);
    }

    /**
     * 返回测试断言使用的目录类型名称。
     *
     * @param type 目录类型
     * @return 名称
     */
    private String typeLabel(HospitalDirectoryType type) {
        return switch (type) {
            case DEPARTMENT -> "科室";
            case DOCTOR -> "医生";
            case WARD -> "病区";
            case BED -> "床位";
        };
    }

    /**
     * 创建HIS成功响应测试数据。
     *
     * @param entry 来源记录
     * @return 成功响应
     */
    private PhisResponse<List<HospitalDirectoryEntry>> success(HospitalDirectoryEntry entry) {
        return new PhisResponse<>(true, "1", List.of(entry), null);
    }

    /**
     * 创建医院目录同步服务及其全部替身依赖。
     *
     * @return 完整可验证的服务依赖
     */
    private Fixture fixture() {
        HospitalDirectorySyncMapper mapper = mock(HospitalDirectorySyncMapper.class);
        MasterDataBatchMapper batchMapper = mock(MasterDataBatchMapper.class);
        when(batchMapper.lockExecution(anyLong(), any())).thenReturn(true);
        List<HospitalDirectorySyncResultVO> saved = new ArrayList<>();
        doAnswer(invocation -> { saved.add(invocation.getArgument(1)); return null; })
                .when(mapper).saveDirectoryResult(anyLong(), any());
        when(mapper.findResults(25L)).thenAnswer(invocation -> List.copyOf(saved));
        PhisService phisService = mock(PhisService.class);
        var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
        TransactionTemplate transactions = new TransactionTemplate(manager);
        when(mapper.classifyDirectory(anyLong(), any())).thenReturn(0);
        when(mapper.countActive(10L)).thenReturn(3L);
        when(mapper.countActiveByType(eq(10L), any(HospitalDirectoryType.class))).thenReturn(1L);
        when(mapper.finishCompleted(eq(25L), any(), any(), any(), any(), eq("admin"))).thenReturn(1);
        ManagementAuditService auditService = mock(ManagementAuditService.class);
        ExchangeRuntimeRecordService exchangeRecords = mock(ExchangeRuntimeRecordService.class);
        return new Fixture(new HospitalDirectorySyncServiceImpl(mapper, phisService,
                exchangeRecords, auditService, transactions, batchMapper), mapper, phisService, exchangeRecords,
                auditService, batchMapper, manager);
    }

    /**
     * 创建处于可执行状态的同步批次快照。
     *
     * @return 当前可执行的同步运行
     */
    private MasterDataBatchSnapshot snapshot() {
        return new MasterDataBatchSnapshot(25L, "BD-TEST", MasterDataScopeType.ORGANIZATION, 10L, "ORG001", "测试机构",
                ParameterEnvironment.TEST, MasterDataCategory.HOSPITAL_DIRECTORY, MasterDataSyncMode.NOT_APPLICABLE,
                "100-003", null, null, "HIS-ORG-001", null, null, null, null, null,
                MasterDataBatchStatus.FETCHING, null,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null,
                null, null, null, null, null, new byte[]{1});
    }


    /**
     * 保存医院目录同步测试所需的服务和替身。
     *
     * @param service 被测服务
     * @param mapper 同步持久化边界
     * @param phisService HIS调用边界
     */
    private record Fixture(HospitalDirectorySyncService service, HospitalDirectorySyncMapper mapper,
                           PhisService phisService, ExchangeRuntimeRecordService exchangeRecords,
                           ManagementAuditService auditService, MasterDataBatchMapper batchMapper,
                           org.springframework.transaction.PlatformTransactionManager transactions) {
    }
}
