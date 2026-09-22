package cn.zqkj.platform.masterdata.service.batch.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.exception.ResourceNotFoundException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.masterdata.domain.batch.dto.MasterDataBatchQuery;
import cn.zqkj.platform.masterdata.domain.batch.dto.StartMasterDataBatchRequest;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCreation;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataSyncMode;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchPageVO;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataSyncBusinessVO;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataSyncOptionsVO;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataSyncSourceVO;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectorySyncResultVO;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectorySyncResultVO;
import cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper;
import cn.zqkj.platform.masterdata.mapper.hospitaldirectory.HospitalDirectorySyncMapper;
import cn.zqkj.platform.masterdata.mapper.medicaldirectory.MedicalDirectorySyncMapper;
import cn.zqkj.platform.masterdata.service.batch.MasterDataBatchService;
import cn.zqkj.platform.masterdata.service.hospitaldirectory.HospitalDirectorySyncService;
import cn.zqkj.platform.masterdata.service.medicaldirectory.MedicalDirectorySyncService;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointRuntimeConfiguration;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.configuration.service.ExternalEndpointResolutionService;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 实现基础数据同步批次的范围校验、幂等创建和权限内查询。
 */
@Service
public class MasterDataBatchServiceImpl implements MasterDataBatchService {

    private static final String SOURCE_SYSTEM_CODE = "PRIMARY_HIS";
    private static final String FULL_SYNC_RANGE_NOTE =
            "用户指定的默认查询窗口：批次创建时刻向前20年至创建时刻；不证明HIS更早或无时间记录已覆盖";
    private static final List<MasterDataCategory> IMPLEMENTED_CATEGORIES = List.of(
            MasterDataCategory.HOSPITAL_DIRECTORY, MasterDataCategory.MEDICAL_DIRECTORY);
    private final MasterDataBatchMapper mapper;
    private final HospitalDirectorySyncMapper hospitalDirectoryMapper;
    private final MedicalDirectorySyncMapper medicalDirectoryMapper;
    private final ManagementAuditService auditService;
    private final ExternalEndpointResolutionService endpointResolutionService;
    private final HospitalDirectorySyncService hospitalSyncService;
    private final MedicalDirectorySyncService medicalSyncService;
    private final TransactionTemplate transactions;

    /**
     * 创建基础数据同步批次服务。
     *
     * @param mapper 基础数据批次持久化边界
     * @param hospitalDirectoryMapper 100-003目录运行事实持久化边界
     * @param medicalDirectoryMapper 100-004/100-005目录运行事实持久化边界
     * @param auditService 管理审计追加服务
     * @param endpointResolutionService 外部接口可用配置解析边界
     * @param hospitalSyncService 医院综合目录同步流程
     * @param medicalSyncService 医疗目录同步流程
     * @param transactions 批次原子抢占使用的短事务
     */
    public MasterDataBatchServiceImpl(
            MasterDataBatchMapper mapper,
            HospitalDirectorySyncMapper hospitalDirectoryMapper,
            MedicalDirectorySyncMapper medicalDirectoryMapper,
            ManagementAuditService auditService,
            ExternalEndpointResolutionService endpointResolutionService,
            HospitalDirectorySyncService hospitalSyncService,
            MedicalDirectorySyncService medicalSyncService,
            TransactionTemplate transactions
    ) {
        this.mapper = mapper;
        this.hospitalDirectoryMapper = hospitalDirectoryMapper;
        this.medicalDirectoryMapper = medicalDirectoryMapper;
        this.auditService = auditService;
        this.endpointResolutionService = endpointResolutionService;
        this.hospitalSyncService = hospitalSyncService;
        this.medicalSyncService = medicalSyncService;
        this.transactions = transactions;
    }

    /**
     * 读取可见批次并原子取得执行权，再按批次类别同步当前目录。
     *
     * <p>抢占事务先提交，再调用 HIS；重复请求在取数前以版本冲突结束。
     * 各目录类型独立提交，失败或结果未知类型保留原数据。</p>
     */
    @Override
    public MasterDataBatchSummaryVO run(long id, byte[] expectedVersion, AccessActor actor) {
        MasterDataBatchSnapshot batch = requireVisibleBatch(id, List.copyOf(actor.organizationCodes()));
        Long enabledOrganizationId = mapper.findEnabledOrganizationId(batch.organizationCode());
        if (batch.organizationId() == null || !batch.organizationId().equals(enabledOrganizationId)) {
            throw new InvalidRequestException("批次机构不存在或已停用");
        }
        if (batch.category() == MasterDataCategory.MEDICAL_DIRECTORY
                && (batch.rangeStart() == null || batch.rangeEnd() == null || batch.sourceOrganizationId() == null)) {
            throw new InvalidRequestException("医疗目录同步批次缺少来源机构或查询时间范围");
        }
        if (batch.sourceEndpointId() != null) {
            var current = endpointResolutionService.findEnabledRuntime(
                    SOURCE_SYSTEM_CODE, batch.environment(), batch.organizationId())
                    .orElseThrow(() -> new ResourceConflictException("批次绑定的HIS来源配置已失效"));
            if (current.endpoint().id() != batch.sourceEndpointId()
                    || !Arrays.equals(current.endpoint().version(), batch.sourceEndpointVersion())) {
                throw new ResourceConflictException("批次创建后HIS来源配置已改变，请重新创建批次");
            }
        }
        MasterDataBatchSnapshot execution = transactions.execute(status -> {
            if (mapper.beginFetch(id, expectedVersion, actor.loginName()) != 1) {
                throw new ResourceConflictException("批次状态已经变化，请重新读取后再操作");
            }
            return requireBatch(id);
        });
        switch (batch.category()) {
            case HOSPITAL_DIRECTORY -> hospitalSyncService.synchronize(execution, actor.userId(), actor.loginName());
            case MEDICAL_DIRECTORY -> medicalSyncService.synchronize(execution, actor.userId(), actor.loginName());
        }
        return toView(requireBatch(id));
    }

    /**
     * 按已保存分项回收中断批次；不调用HIS，并通过行版本撤销旧执行者的写入权。
     *
     * <p>入口须有同步权限；按ID读取仍带机构范围。未完成的类型不推定成功，
     * 如需重取数据，操作人必须核查后创建新批次。</p>
     */
    @Override
    public MasterDataBatchSummaryVO recover(long id, byte[] expectedVersion, AccessActor actor) {
        MasterDataBatchSnapshot batch = requireVisibleBatch(id, List.copyOf(actor.organizationCodes()));
        if ((batch.status() != MasterDataBatchStatus.FETCHING && batch.status() != MasterDataBatchStatus.RESULT_UNKNOWN)
                || !java.util.Arrays.equals(batch.version(), expectedVersion)) {
            throw new ResourceConflictException("仅能恢复版本未变化的运行中或结果未知批次");
        }
        switch (batch.category()) {
            case HOSPITAL_DIRECTORY -> hospitalSyncService.completeRecordedResults(batch, actor.userId(), actor.loginName());
            case MEDICAL_DIRECTORY -> medicalSyncService.completeRecordedResults(batch, actor.userId(), actor.loginName());
        }
        return toView(requireBatch(id));
    }

    /**
     * 分页读取获准机构范围内的同步批次。
     *
     * <p>调用入口负责显式机构授权；固定机构范围后再执行数量和分页查询，避免两次查询范围漂移。</p>
     */
    @Transactional(readOnly = true)
    @Override
    public MasterDataBatchPageVO findPage(MasterDataBatchQuery query, List<String> allowedOrganizationCodes) {
        List<String> organizationCodes = List.copyOf(allowedOrganizationCodes);
        long total = mapper.countPage(query, organizationCodes);
        List<MasterDataBatchSummaryVO> items = mapper.findPage(query, organizationCodes).stream()
                .map(this::toView)
                .toList();
        return new MasterDataBatchPageVO(items, total, query.page(), query.pageSize());
    }

    /**
     * 读取获准机构范围内的单个批次，不存在或不可见时返回资源不存在。
     *
     * <p>机构范围进入 SQL 条件；不存在和不可见都不返回批次内容。</p>
     */
    @Transactional(readOnly = true)
    @Override
    public MasterDataBatchSummaryVO get(long id, List<String> allowedOrganizationCodes) {
        MasterDataBatchSnapshot snapshot = requireVisibleBatch(id, allowedOrganizationCodes);
        return toView(snapshot);
    }

    /**
     * 读取医院综合目录批次已保存的分类型同步结果。
     *
     * <p>分项结果只来自批次运行时落库的事实，不以汇总数量倒推旧批次的类型明细。</p>
     */
    @Transactional(readOnly = true)
    @Override
    public List<HospitalDirectorySyncResultVO> findHospitalDirectoryResults(
            long id, List<String> allowedOrganizationCodes) {
        MasterDataBatchSnapshot snapshot = requireVisibleBatch(id, allowedOrganizationCodes);
        if (snapshot.category() != MasterDataCategory.HOSPITAL_DIRECTORY) {
            throw new InvalidRequestException("当前批次不是100-003医院综合目录业务");
        }
        return hospitalDirectoryMapper.findResults(id);
    }

    /**
     * 读取医疗目录批次已保存的分类型同步结果。
     *
     * <p>结果仅来自已完成的100-004/100-005运行事实，不会重新调用HIS或根据总计猜测类型明细。</p>
     */
    @Transactional(readOnly = true)
    @Override
    public List<MedicalDirectorySyncResultVO> findMedicalDirectoryResults(
            long id, List<String> allowedOrganizationCodes) {
        MasterDataBatchSnapshot snapshot = requireVisibleBatch(id, allowedOrganizationCodes);
        if (snapshot.category() != MasterDataCategory.MEDICAL_DIRECTORY) {
            throw new InvalidRequestException("当前批次不是100-004/100-005医疗目录业务");
        }
        return medicalDirectoryMapper.findResults(id);
    }

    /**
     * 列出获准机构可用的 HIS 来源环境和已实现同步业务。
     *
     * <p>只返回当前账号有权访问且运行时地址、凭证均可解析的同步来源。</p>
     */
    @Transactional(readOnly = true)
    @Override
    public MasterDataSyncOptionsVO findSyncOptions(List<String> allowedOrganizationCodes) {
        var scopes = endpointResolutionService.findAvailableScopes(
                SOURCE_SYSTEM_CODE, List.copyOf(allowedOrganizationCodes));
        var grouped = new LinkedHashMap<String, SourceBuilder>();
        for (var scope : scopes) {
            SourceBuilder source = grouped.get(scope.organizationCode());
            if (source == null) {
                source = new SourceBuilder(scope.organizationCode(), scope.organizationName(), new ArrayList<>());
                grouped.put(scope.organizationCode(), source);
            }
            source.environments().add(scope.environment());
        }
        List<MasterDataSyncSourceVO> sources = new ArrayList<>();
        for (SourceBuilder source : grouped.values()) {
            sources.add(new MasterDataSyncSourceVO(
                    source.organizationCode(), source.organizationName(), List.copyOf(source.environments())));
        }
        List<MasterDataSyncBusinessVO> businesses = new ArrayList<>();
        for (MasterDataCategory category : IMPLEMENTED_CATEGORIES) {
            businesses.add(new MasterDataSyncBusinessVO(
                        category,
                        category.displayName(),
                        category.dataTradeCode(),
                        category.countTradeCode(),
                        category == MasterDataCategory.MEDICAL_DIRECTORY));
        }
        return new MasterDataSyncOptionsVO(List.copyOf(sources), List.copyOf(businesses));
    }

    /**
     * 为已启用机构创建待执行同步批次，并记录创建审计。
     *
     * <p>全量模式由服务端冻结创建时刻及向前20年的查询起点，不接受调用方指定起止时间。
     * 在同一事务内校验来源范围、创建幂等批次并追加成功审计；活动范围或请求标识重复时明确拒绝。</p>
     */
    @Transactional
    @Override
    public MasterDataBatchSummaryVO start(StartMasterDataBatchRequest request, AccessActor actor) {
        if (!request.isTimeRangeValid()) throw new InvalidRequestException("同步模式与来源查询范围不匹配");
        Long organizationId = mapper.findEnabledOrganizationId(request.organizationCode());
        if (organizationId == null) throw new InvalidRequestException("所选机构不存在或已停用");
        ExternalEndpointRuntimeConfiguration runtime = endpointResolutionService.findEnabledRuntime(
                        SOURCE_SYSTEM_CODE, request.environment(), organizationId)
                .orElseThrow(() -> new InvalidRequestException(
                        "所选机构在该环境没有通过100-008校验的基层HIS接口配置"));
        String verifiedSourceOrganizationId = runtime.endpoint() == null ? null
                : Func.trimToNull(runtime.endpoint().sourceOrganizationId());
        if (verifiedSourceOrganizationId == null) {
            throw new InvalidRequestException("该机构的基层HIS配置未保存100-008来源机构结果，不能发起同步");
        }
        if (request.category() == MasterDataCategory.MEDICAL_DIRECTORY
                && mapper.hasMedicalCatalogFromOtherEnvironment(organizationId, request.environment())) {
            throw new ResourceConflictException("该机构当前医疗目录来自另一接口环境，不能交叉写入");
        }
        // 当前HIS的目录交易使用平台机构编码；100-008返回ID只用于确认配置身份。
        // 依据：2026-09-20真实联调，不能以来源ID替换已验证可用的交易参数。
        String sourceOrganizationId = request.organizationCode();
        OffsetDateTime rangeStart = request.rangeStart();
        OffsetDateTime rangeEnd = request.rangeEnd();
        String fullRuleEvidence = null;
        if (request.mode() == MasterDataSyncMode.FULL) {
            rangeEnd = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
            rangeStart = rangeEnd.minusYears(20);
            if (!rangeEnd.isAfter(rangeStart)) {
                throw new InvalidRequestException("医疗目录全量查询范围无效");
            }
            fullRuleEvidence = FULL_SYNC_RANGE_NOTE;
        }
        MasterDataBatchCreation creation = new MasterDataBatchCreation(
                request.requestKey(), request.organizationCode(), request.environment(), request.category(),
                request.mode(), Func.toUtc(rangeStart), Func.toUtc(rangeEnd), runtime.endpoint().id(),
                runtime.endpoint().version(), fullRuleEvidence);
        String batchNo = "BD-" + Func.simpleUuid()
                .substring(0, 20).toUpperCase(Locale.ROOT);
        try {
            long id = mapper.create(batchNo, SOURCE_SYSTEM_CODE, creation,
                    organizationId, sourceOrganizationId, actor.loginName());
            MasterDataBatchSummaryVO result = toView(requireBatch(id));
            auditService.append(new ManagementAuditCommand(
                    actor.userId(), actor.loginName(),
                    organizationId,
                    request.organizationCode(),
                    "MASTER_DATA_BATCH_CREATED",
                    "MASTER_DATA_BATCH",
                    result.batchNo(),
                    "SUCCESS",
                    "已创建" + request.category().displayName() + "同步批次；尚未开始取得数据",
                    null
            ));
            return result;
        } catch (DuplicateKeyException exception) {
            throw new ResourceConflictException("相同请求或同步范围已经存在活动批次，请先查询现有批次");
        }
    }

    /**
     * 取消尚未执行的批次，保留批次历史并释放活动范围。
     *
     * <p>仅允许取消尚未执行的批次，并以调用方最近读取的行版本防止覆盖并发状态变化。</p>
     */
    @Transactional
    @Override
    public MasterDataBatchSummaryVO cancel(long id, byte[] expectedVersion, AccessActor actor) {
        MasterDataBatchSnapshot current = requireVisibleBatch(id, List.copyOf(actor.organizationCodes()));
        if (current.status() != MasterDataBatchStatus.CREATED) {
            throw new ResourceConflictException("只有尚未开始的批次可以取消");
        }
        int affected = mapper.cancel(id, expectedVersion, actor.loginName(), List.copyOf(actor.organizationCodes()));
        if (affected != 1) {
            throw new ResourceConflictException("批次状态已经变化，请重新读取后再操作");
        }
        MasterDataBatchSummaryVO result = toView(requireBatch(id));
        auditService.append(new ManagementAuditCommand(
                actor.userId(), actor.loginName(),
                null,
                current.organizationCode(),
                "MASTER_DATA_BATCH_CANCELLED",
                "MASTER_DATA_BATCH",
                current.batchNo(),
                "SUCCESS",
                "已取消尚未执行的" + current.category().displayName() + "同步批次",
                null
        ));
        return result;
    }

    /**
     * 读取同步批次；不存在时抛出资源不存在异常。
     *
     * @param id 批次主键
     * @return 已存在批次
     */
    private MasterDataBatchSnapshot requireBatch(long id) {
        MasterDataBatchSnapshot snapshot = mapper.findById(id);
        if (snapshot == null) throw new ResourceNotFoundException("同步批次不存在");
        return snapshot;
    }

    /**
     * 读取机构范围内的批次，隐藏范围外批次的存在性和内容。
     *
     * @param id 批次主键
     * @param allowedOrganizationCodes 调用入口确认的机构范围
     * @return 当前范围内的批次快照
     */
    private MasterDataBatchSnapshot requireVisibleBatch(long id, List<String> allowedOrganizationCodes) {
        MasterDataBatchSnapshot snapshot = mapper.findVisibleById(id, List.copyOf(allowedOrganizationCodes));
        if (snapshot == null) throw new ResourceNotFoundException("同步批次不存在");
        return snapshot;
    }

    /**
     * 将同步批次快照转换为API输出。
     *
     * @param snapshot 数据库快照
     * @return API摘要
     */
    private MasterDataBatchSummaryVO toView(MasterDataBatchSnapshot snapshot) {
        return new MasterDataBatchSummaryVO(
                snapshot.id(), snapshot.batchNo(), snapshot.scopeType(), snapshot.organizationCode(),
                snapshot.organizationName(), snapshot.environment(), snapshot.category(), snapshot.syncMode(),
                snapshot.dataTradeCode(),
                snapshot.countTradeCode(), snapshot.sourceType(), snapshot.sourceOrganizationId(),
                snapshot.fullRuleEvidence(),
                Func.toOffset(snapshot.rangeStart()),
                Func.toOffset(snapshot.rangeEnd()), snapshot.status(), snapshot.counts(),
                Func.toOffset(snapshot.startedAt()), Func.toOffset(snapshot.finishedAt()),
                Func.toOffset(snapshot.completedAt()),
                snapshot.failureCode(), snapshot.failureSummary(), Func.encodeBase64(snapshot.version())
        );
    }





    /** 汇总同一机构下的可用环境。 */
    private record SourceBuilder(
            String organizationCode,
            String organizationName,
            List<ParameterEnvironment> environments
    ) {
    }
}
