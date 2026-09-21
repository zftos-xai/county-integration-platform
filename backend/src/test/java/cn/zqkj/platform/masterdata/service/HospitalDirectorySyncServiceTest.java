package cn.zqkj.platform.masterdata.service;

import cn.zqkj.platform.his.domain.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.his.domain.model.HospitalDirectoryEntry;
import cn.zqkj.platform.his.domain.model.HospitalDirectoryType;
import cn.zqkj.platform.his.domain.model.PhisResponse;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.masterdata.domain.model.MasterDataBatchCounts;
import cn.zqkj.platform.masterdata.domain.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.model.MasterDataScopeType;
import cn.zqkj.platform.masterdata.domain.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.masterdata.mapper.HospitalDirectorySyncMapper;
import cn.zqkj.platform.masterdata.mapper.MasterDataBatchMapper;
import cn.zqkj.platform.masterdata.service.impl.HospitalDirectorySyncServiceImpl;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.service.ManagementAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证100-003按目录类型独立对账，不再使用整批发布门槛。 */
class HospitalDirectorySyncServiceTest {

    /** 验证一个目录类型失败时，其他成功类型仍直接对账，失败类型不进入失效标记。 */
    @Test
    void synchronizesSuccessfulTypesWhenAnotherTypeIsRejectedByHis() {
        Fixture fixture = fixture();
        when(fixture.phisService().queryHospitalDirectory(eq(10L), eq(ParameterEnvironment.TEST), any(HospitalDirectoryQuery.class)))
                .thenAnswer(invocation -> {
                    HospitalDirectoryType type = invocation.getArgument(2, HospitalDirectoryQuery.class).directoryType();
                    return type == HospitalDirectoryType.DOCTOR
                            ? new PhisResponse<>(false, "0", null, "无权访问")
                            : success(entry(type.name(), typeLabel(type)));
                });

        fixture.service().fetchValidateAndReconcile(25L, new byte[]{1}, actor());

        verify(fixture.mapper(), times(3)).markMissingDirectoryInvalid(eq(25L), eq(10L), any(HospitalDirectoryType.class));
        verify(fixture.mapper(), never()).markMissingDirectoryInvalid(eq(25L), eq(10L), eq(HospitalDirectoryType.DOCTOR));
        verify(fixture.mapper()).finishCompleted(eq(25L), eq("COMPLETED_WITH_ERRORS"), eq(3L),
                eq(0L), eq(0L), eq(0L), eq(3L), eq(0L), eq(0L), eq(0L), eq(3L),
                eq("HIS_PARTIAL_FAILURE"), contains("医生"), eq("admin"));
        verify(fixture.mapper(), times(4)).saveDirectoryResult(eq(25L), any());
        verify(fixture.auditService()).recordSuccess(any());
    }

    /** 验证一类数据字段不合法时，不会把该类型HIS未返回的旧记录误标为无效。 */
    @Test
    void doesNotInvalidateTypeWhenAutomaticValidationFails() {
        Fixture fixture = fixture();
        when(fixture.phisService().queryHospitalDirectory(eq(10L), eq(ParameterEnvironment.TEST), any(HospitalDirectoryQuery.class)))
                .thenAnswer(invocation -> {
                    HospitalDirectoryType type = invocation.getArgument(2, HospitalDirectoryQuery.class).directoryType();
                    return type == HospitalDirectoryType.WARD ? success(entry("", "病区")) : success(entry(type.name(), typeLabel(type)));
                });

        fixture.service().fetchValidateAndReconcile(25L, new byte[]{1}, actor());

        verify(fixture.mapper(), never()).markMissingDirectoryInvalid(eq(25L), eq(10L), eq(HospitalDirectoryType.WARD));
        verify(fixture.mapper()).finishCompleted(eq(25L), eq("COMPLETED_WITH_ERRORS"), eq(4L),
                eq(0L), eq(1L), eq(0L), eq(3L), eq(0L), eq(0L), eq(0L), eq(3L),
                eq("HIS_PARTIAL_FAILURE"), contains("病区"), eq("admin"));
    }

    /** 验证单个类型通信结果未知时，不阻断其他类型，并准确记录部分结果未知。 */
    @Test
    void preservesOtherTypesWhenOneTypeResultIsUnknown() {
        Fixture fixture = fixture();
        when(fixture.phisService().queryHospitalDirectory(eq(10L), eq(ParameterEnvironment.TEST), any(HospitalDirectoryQuery.class)))
                .thenAnswer(invocation -> {
                    HospitalDirectoryType type = invocation.getArgument(2, HospitalDirectoryQuery.class).directoryType();
                    if (type == HospitalDirectoryType.BED) {
                        throw new cn.zqkj.platform.his.exception.PhisCommunicationException("超时");
                    }
                    return success(entry(type.name(), typeLabel(type)));
                });

        fixture.service().fetchValidateAndReconcile(25L, new byte[]{1}, actor());

        verify(fixture.mapper()).finishCompleted(eq(25L), eq("COMPLETED_WITH_UNKNOWN"), eq(3L),
                eq(0L), eq(0L), eq(0L), eq(3L), eq(0L), eq(0L), eq(0L), eq(3L),
                eq("HIS_PARTIAL_RESULT_UNKNOWN"), contains("床位"), eq("admin"));
    }

    /**
     * 创建具有测试机构范围的服务层操作人。
     *
     * @return 有权限的操作人
     */
    private AccessActor actor() {
        return new AccessActor(1L, "admin", Set.of("ORG001"));
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
        MasterDataBatchService batchService = mock(MasterDataBatchService.class);
        PhisService phisService = mock(PhisService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, java.util.function.Consumer.class).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
        doAnswer(invocation -> invocation.getArgument(0, TransactionCallback.class).doInTransaction(null))
                .when(transactions).execute(any());
        when(batchMapper.findById(25L)).thenReturn(snapshot());
        when(batchMapper.findEnabledOrganizationId("ORG001")).thenReturn(10L);
        when(mapper.beginFetch(25L, new byte[]{1}, "admin")).thenReturn(1);
        when(mapper.classifyDirectory(anyLong(), any())).thenReturn(0);
        when(mapper.countActive(10L)).thenReturn(3L);
        when(mapper.countActiveByType(eq(10L), any(HospitalDirectoryType.class))).thenReturn(1L);
        when(mapper.finishCompleted(eq(25L), any(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
                anyLong(), anyLong(), anyLong(), any(), any(), eq("admin"))).thenReturn(1);
        when(batchService.get(25L, actor())).thenReturn(summary());
        ManagementAuditService auditService = mock(ManagementAuditService.class);
        return new Fixture(new HospitalDirectorySyncServiceImpl(mapper, batchMapper, batchService, phisService,
                auditService, transactions), mapper, phisService, auditService);
    }

    /**
     * 创建处于可执行状态的同步批次快照。
     *
     * @return 当前可执行的同步运行
     */
    private MasterDataBatchSnapshot snapshot() {
        return new MasterDataBatchSnapshot(25L, "BD-TEST", MasterDataScopeType.ORGANIZATION, "ORG001", "测试机构",
                ParameterEnvironment.TEST, MasterDataCategory.HOSPITAL_DIRECTORY, "100-003", null, null,
                "HIS-ORG-001", null, null, MasterDataBatchStatus.CREATED, null,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null,
                null, null, null, null, null, new byte[]{1});
    }

    /**
     * 创建用于断言的批次API摘要。
     *
     * @return 简化的已完成响应
     */
    private MasterDataBatchSummaryVO summary() {
        return new MasterDataBatchSummaryVO(25L, "BD-TEST", MasterDataScopeType.ORGANIZATION, "ORG001", "测试机构",
                ParameterEnvironment.TEST, MasterDataCategory.HOSPITAL_DIRECTORY, "100-003", null, null,
                "HIS-ORG-001", null, null, MasterDataBatchStatus.COMPLETED,
                new MasterDataBatchCounts(null, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L),
                null, null, null, null, null, "AQ==");
    }

    /**
     * 保存医院目录同步测试所需的服务和替身。
     *
     * @param service 被测服务
     * @param mapper 同步持久化边界
     * @param phisService HIS调用边界
     */
    private record Fixture(HospitalDirectorySyncService service, HospitalDirectorySyncMapper mapper,
                           PhisService phisService, ManagementAuditService auditService) {
    }
}
