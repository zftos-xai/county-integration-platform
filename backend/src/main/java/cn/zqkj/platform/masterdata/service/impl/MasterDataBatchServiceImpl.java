package cn.zqkj.platform.masterdata.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.exception.ResourceNotFoundException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.masterdata.domain.dto.MasterDataBatchQuery;
import cn.zqkj.platform.masterdata.domain.dto.StartMasterDataBatchRequest;
import cn.zqkj.platform.masterdata.domain.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.model.HospitalDirectorySyncResult;
import cn.zqkj.platform.masterdata.domain.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.model.MasterDataScopeType;
import cn.zqkj.platform.masterdata.domain.vo.MasterDataBatchPageVO;
import cn.zqkj.platform.masterdata.domain.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.masterdata.domain.vo.HospitalDirectorySyncResultVO;
import cn.zqkj.platform.masterdata.domain.vo.MasterDataSyncSourceVO;
import cn.zqkj.platform.masterdata.domain.vo.MasterDataSyncBusinessVO;
import cn.zqkj.platform.masterdata.domain.vo.MasterDataSyncOptionsVO;
import cn.zqkj.platform.masterdata.mapper.MasterDataBatchMapper;
import cn.zqkj.platform.masterdata.service.MasterDataBatchService;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.service.ManagementAuditService;
import cn.zqkj.platform.system.domain.model.ExternalEndpointRuntimeConfiguration;
import cn.zqkj.platform.system.service.ExternalEndpointResolutionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * 实现基础数据同步批次的范围校验、幂等创建和权限内查询。
 */
@Service
public class MasterDataBatchServiceImpl implements MasterDataBatchService {

    private static final String SOURCE_SYSTEM_CODE = "PRIMARY_HIS";
    private static final List<MasterDataCategory> IMPLEMENTED_CATEGORIES = List.of(MasterDataCategory.HOSPITAL_DIRECTORY);
    private static final int MAXIMUM_PAGE_SIZE = 100;
    private final MasterDataBatchMapper mapper;
    private final ManagementAuditService auditService;
    private final ExternalEndpointResolutionService endpointResolutionService;

    /**
     * 创建基础数据同步批次服务。
     *
     * @param mapper 基础数据批次持久化边界
     * @param auditService 管理审计追加服务
     * @param endpointResolutionService 外部接口可用配置解析边界
     */
    public MasterDataBatchServiceImpl(
            MasterDataBatchMapper mapper,
            ManagementAuditService auditService,
            ExternalEndpointResolutionService endpointResolutionService
    ) {
        this.mapper = mapper;
        this.auditService = auditService;
        this.endpointResolutionService = endpointResolutionService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>先校验筛选范围，再将查询限制在操作人可访问的机构集合内。</p>
     */
    @Transactional(readOnly = true)
    @Override
    public MasterDataBatchPageVO findPage(MasterDataBatchQuery query, AccessActor actor) {
        validateQuery(query);
        if (query.organizationCode() != null && !actor.canAccess(query.organizationCode())) {
            throw new AccessDeniedException("当前账号无权访问该机构");
        }
        List<String> organizationCodes = new ArrayList<>(actor.organizationCodes());
        long total = mapper.countPage(query, organizationCodes);
        List<MasterDataBatchSummaryVO> items = mapper.findPage(query, organizationCodes).stream()
                .map(this::toView)
                .toList();
        return new MasterDataBatchPageVO(items, total, query.page(), query.pageSize());
    }

    /**
     * {@inheritDoc}
     *
     * <p>读取批次后再次执行机构可见性校验，不能只依赖控制器权限。</p>
     */
    @Transactional(readOnly = true)
    @Override
    public MasterDataBatchSummaryVO get(long id, AccessActor actor) {
        MasterDataBatchSnapshot snapshot = requireBatch(id);
        requireVisible(snapshot, actor);
        return toView(snapshot);
    }

    /**
     * {@inheritDoc}
     *
     * <p>分项结果只来自批次运行时落库的事实，不以汇总数量倒推旧批次的类型明细。</p>
     */
    @Transactional(readOnly = true)
    @Override
    public List<HospitalDirectorySyncResultVO> findHospitalDirectoryResults(long id, AccessActor actor) {
        MasterDataBatchSnapshot snapshot = requireBatch(id);
        requireVisible(snapshot, actor);
        if (snapshot.category() != MasterDataCategory.HOSPITAL_DIRECTORY) {
            throw new InvalidRequestException("当前批次不是100-003医院综合目录业务");
        }
        return mapper.findDirectoryResults(id).stream().map(this::toDirectoryResultView).toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>只返回当前账号有权访问且运行时地址、凭证均可解析的同步来源。</p>
     */
    @Transactional(readOnly = true)
    @Override
    public MasterDataSyncOptionsVO findSyncOptions(AccessActor actor) {
        var scopes = endpointResolutionService.findAvailableScopes(
                SOURCE_SYSTEM_CODE, new ArrayList<>(actor.organizationCodes()));
        var grouped = new LinkedHashMap<String, SourceBuilder>();
        scopes.forEach(scope -> grouped
                .computeIfAbsent(scope.organizationCode(), ignored ->
                        new SourceBuilder(scope.organizationCode(), scope.organizationName(), new ArrayList<>()))
                .environments().add(scope.environment()));
        List<MasterDataSyncSourceVO> sources = grouped.values().stream()
                .map(source -> new MasterDataSyncSourceVO(
                        source.organizationCode(), source.organizationName(), source.environments()))
                .toList();
        List<MasterDataSyncBusinessVO> businesses = IMPLEMENTED_CATEGORIES.stream()
                .map(category -> new MasterDataSyncBusinessVO(
                        category,
                        category.displayName(),
                        category.dataTrade().code()))
                .toList();
        return new MasterDataSyncOptionsVO(sources, businesses);
    }

    /**
     * {@inheritDoc}
     *
     * <p>在同一事务内校验来源范围、创建幂等批次并追加成功审计；活动范围或请求标识重复时明确拒绝。</p>
     */
    @Transactional
    @Override
    public MasterDataBatchSummaryVO start(StartMasterDataBatchRequest request, AccessActor actor) {
        StartValues values = validateStart(request, actor);
        String batchNo = "BD-" + Func.simpleUuid()
                .substring(0, 20).toUpperCase(Locale.ROOT);
        try {
            long id = mapper.create(
                    batchNo,
                    values.requestKey(),
                    SOURCE_SYSTEM_CODE,
                    values.organizationId(),
                    values.environment(),
                    values.category(),
                    values.category().scopeType(),
                    values.category().dataTrade().code(),
                    values.category().countTrade() == null ? null : values.category().countTrade().code(),
                    values.sourceType(),
                    values.sourceOrganizationId(),
                    values.rangeStart(),
                    values.rangeEnd(),
                    values.diagnosisCategory(),
                    values.diagnosisVersion(),
                    actor.loginName()
            );
            MasterDataBatchSummaryVO result = toView(requireBatch(id));
            auditService.recordSuccess(new ManagementAuditCommand(
                    actor,
                    null,
                    values.organizationId(),
                    values.organizationCode(),
                    "MASTER_DATA_BATCH_CREATED",
                    "MASTER_DATA_BATCH",
                    result.batchNo(),
                    "SUCCESS",
                    "已创建" + values.category().displayName() + "同步批次；尚未开始取得数据",
                    null
            ));
            return result;
        } catch (DuplicateKeyException exception) {
            throw new ResourceConflictException("相同请求或同步范围已经存在活动批次，请先查询现有批次");
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>仅允许取消尚未执行的批次，并以调用方最近读取的行版本防止覆盖并发状态变化。</p>
     */
    @Transactional
    @Override
    public MasterDataBatchSummaryVO cancel(long id, byte[] expectedVersion, AccessActor actor) {
        MasterDataBatchSnapshot current = requireBatch(id);
        requireVisible(current, actor);
        if (expectedVersion == null || expectedVersion.length == 0) {
            throw new InvalidRequestException("必须提供最近读取的批次版本");
        }
        if (current.status() != MasterDataBatchStatus.CREATED) {
            throw new ResourceConflictException("只有尚未开始的批次可以取消");
        }
        int affected = mapper.cancel(id, expectedVersion, actor.loginName());
        if (affected != 1) {
            throw new ResourceConflictException("批次状态已经变化，请重新读取后再操作");
        }
        MasterDataBatchSummaryVO result = toView(requireBatch(id));
        auditService.recordSuccess(new ManagementAuditCommand(
                actor,
                null,
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
     * 校验分页范围、筛选条件和机构访问边界。
     *
     * @param query 查询条件
     */
    private void validateQuery(MasterDataBatchQuery query) {
        if (query == null || query.page() < 1 || query.pageSize() < 1 || query.pageSize() > MAXIMUM_PAGE_SIZE) {
            throw new InvalidRequestException("分页参数不符合要求");
        }
        if (query.startedFrom() != null && query.startedTo() != null
                && query.startedFrom().isAfter(query.startedTo())) {
            throw new InvalidRequestException("开始时间不能晚于结束时间");
        }
    }

    /**
     * 校验同步业务、来源端点、机构范围和时间参数。
     *
     * @param request 原始请求
     * @param actor 当前操作人
     * @return 规范化后的创建值
     */
    private StartValues validateStart(StartMasterDataBatchRequest request, AccessActor actor) {
        if (request == null || request.category() == null) {
            throw new InvalidRequestException("必须选择同步数据类别");
        }
        String requestKey = Func.requireText(request.requestKey(), "请求标识", 64);
        if (requestKey.length() < 20) {
            throw new InvalidRequestException("请求标识长度不能少于20位");
        }
        MasterDataCategory category = request.category();
        if (!IMPLEMENTED_CATEGORIES.contains(category)) {
            throw new InvalidRequestException("所选基础数据业务尚未开放同步");
        }
        if (request.environment() == null) throw new InvalidRequestException("必须选择运行环境");
        Long organizationId = null;
        String sourceOrganizationId = null;
        String organizationCode = Func.trimToNull(request.organizationCode());
        if (category.scopeType() == MasterDataScopeType.ORGANIZATION) {
            if (organizationCode == null) throw new InvalidRequestException("必须选择同步机构");
            if (!actor.canAccess(organizationCode)) throw new AccessDeniedException("当前账号无权访问该机构");
            organizationId = mapper.findEnabledOrganizationId(organizationCode);
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
            // 实测的100-003以平台机构编码限定数据范围；100-008的ID只作为配置身份稳定性证据。
            sourceOrganizationId = organizationCode;
        } else if (organizationCode != null) {
            throw new InvalidRequestException("平台公共目录不能指定机构");
        }

        return new StartValues(
                requestKey, organizationId, organizationCode, sourceOrganizationId, request.environment(), category,
                null, null, null, null, null
        );
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
     * 校验当前操作人可以查看目标同步批次。
     *
     * @param snapshot 批次快照
     * @param actor 当前操作人
     */
    private void requireVisible(MasterDataBatchSnapshot snapshot, AccessActor actor) {
        if (snapshot.scopeType() == MasterDataScopeType.ORGANIZATION
                && !actor.canAccess(snapshot.organizationCode())) {
            throw new AccessDeniedException("当前账号无权访问该机构");
        }
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
                snapshot.organizationName(), snapshot.environment(), snapshot.category(), snapshot.dataTradeCode(),
                snapshot.countTradeCode(), snapshot.sourceType(), snapshot.sourceOrganizationId(),
                Func.toOffset(snapshot.rangeStart()),
                Func.toOffset(snapshot.rangeEnd()), snapshot.status(), snapshot.counts(),
                Func.toOffset(snapshot.startedAt()), Func.toOffset(snapshot.finishedAt()),
                Func.toOffset(snapshot.completedAt()),
                snapshot.failureCode(), snapshot.failureSummary(), Func.encodeBase64(snapshot.version())
        );
    }

    /**
     * 将已落库的单目录类型结果转换为API输出。
     *
     * @param result 分项运行事实
     * @return 管理端只读分项结果
     */
    private HospitalDirectorySyncResultVO toDirectoryResultView(HospitalDirectorySyncResult result) {
        return new HospitalDirectorySyncResultVO(
                result.directoryType(), result.status(), result.returnedCount(), result.duplicateCount(),
                result.invalidCount(), result.conflictCount(), result.createdCount(), result.updatedCount(),
                result.unchangedCount(), result.sourceMissingCount(), result.activeCount(), result.failureSummary()
        );
    }

    /**
     * 保存通过业务组合校验的批次创建值。
     *
     * @param requestKey 请求标识
     * @param organizationId 可选机构主键
     * @param organizationCode 可选机构代码
     * @param sourceOrganizationId 已验证来源机构标识
     * @param environment 来源接口运行环境
     * @param category 数据类别
     * @param sourceType 可选来源目录类型
     * @param rangeStart 可选查询范围开始UTC时间
     * @param rangeEnd 可选查询范围结束UTC时间
     * @param diagnosisCategory 可选疾病类别
     * @param diagnosisVersion 可选诊断版本
     */
    private record StartValues(
            String requestKey,
            Long organizationId,
            String organizationCode,
            String sourceOrganizationId,
            cn.zqkj.platform.system.domain.model.ParameterEnvironment environment,
            MasterDataCategory category,
            String sourceType,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            String diagnosisCategory,
            String diagnosisVersion
    ) {
    }

    /** 汇总同一机构下的可用环境。 */
    private record SourceBuilder(
            String organizationCode,
            String organizationName,
            List<cn.zqkj.platform.system.domain.model.ParameterEnvironment> environments
    ) {
    }
}
