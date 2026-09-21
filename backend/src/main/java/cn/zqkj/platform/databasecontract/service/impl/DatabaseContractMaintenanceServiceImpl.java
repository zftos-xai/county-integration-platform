package cn.zqkj.platform.databasecontract.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.exception.ResourceNotFoundException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.databasecontract.domain.dto.CreateDatabaseContractPlanRequest;
import cn.zqkj.platform.databasecontract.domain.model.DatabaseContractPlanItemSnapshot;
import cn.zqkj.platform.databasecontract.domain.model.DatabaseContractPlanSnapshot;
import cn.zqkj.platform.databasecontract.domain.model.DatabaseContractPlanStatus;
import cn.zqkj.platform.databasecontract.domain.vo.DatabaseContractInspectionVO;
import cn.zqkj.platform.databasecontract.domain.vo.DatabaseContractIssueVO;
import cn.zqkj.platform.databasecontract.domain.vo.DatabaseContractPlanItemVO;
import cn.zqkj.platform.databasecontract.domain.vo.DatabaseContractPlanVO;
import cn.zqkj.platform.databasecontract.mapper.DatabaseContractPlanMapper;
import cn.zqkj.platform.databasecontract.service.DatabaseContractMaintenanceService;
import cn.zqkj.platform.framework.database.DatabaseContractInspection;
import cn.zqkj.platform.framework.database.DatabaseContractInspectionService;
import cn.zqkj.platform.framework.database.DatabaseContractRepairPlanner;
import cn.zqkj.platform.framework.database.DatabaseContractRepairSql;
import cn.zqkj.platform.framework.database.DatabaseContractViolation;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 实现数据库契约方案创建、双人审批、事务DDL、复验和执行前取消。 */
@Service
public class DatabaseContractMaintenanceServiceImpl implements DatabaseContractMaintenanceService {

    private final DatabaseContractInspectionService inspectionService;
    private final DatabaseContractRepairPlanner repairPlanner;
    private final DatabaseContractPlanMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ManagementAuditService auditService;

    /**
     * 创建数据库契约维护服务。
     *
     * @param inspectionService 统一检查核心
     * @param repairPlanner 受控DDL规划器
     * @param mapper 方案持久化边界
     * @param jdbcTemplate DDL执行入口
     * @param transactionTemplate 数据库事务模板
     * @param auditService 管理审计服务
     */
    public DatabaseContractMaintenanceServiceImpl(
            DatabaseContractInspectionService inspectionService,
            DatabaseContractRepairPlanner repairPlanner,
            DatabaseContractPlanMapper mapper,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            ManagementAuditService auditService) {
        this.inspectionService = inspectionService;
        this.repairPlanner = repairPlanner;
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.auditService = auditService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>直接读取当前数据库结构并与代码契约比较，不创建维护方案，也不执行DDL。</p>
     */
    @Override
    public DatabaseContractInspectionVO inspect() {
        return toInspectionView(inspectionService.inspect());
    }

    /**
     * {@inheritDoc}
     *
     * <p>按最近操作时间读取方案摘要，不触发实时数据库扫描。</p>
     */
    @Transactional(readOnly = true)
    @Override
    public List<DatabaseContractPlanVO> findRecentPlans() {
        return mapper.findRecent().stream().map(this::toView).toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>读取方案及其冻结的差异项，保证审批人看到的是创建时的执行内容。</p>
     */
    @Transactional(readOnly = true)
    @Override
    public DatabaseContractPlanVO getPlan(long id) {
        return toView(requirePlan(id));
    }

    /**
     * {@inheritDoc}
     *
     * <p>基于实时扫描结果冻结所选差异及DDL预览；已消失、重复或不可识别的差异不会进入方案。</p>
     */
    @Transactional
    @Override
    public DatabaseContractPlanVO createPlan(CreateDatabaseContractPlanRequest request, AccessActor actor) {
        DatabaseContractInspection inspection = inspectionService.inspect();
        Map<String, DatabaseContractIssueVO> current = issueMap(inspection);
        List<DatabaseContractIssueVO> selected = request.issueKeys().stream().distinct().map(key -> {
            DatabaseContractIssueVO issue = current.get(key);
            if (issue == null) {
                throw new ResourceConflictException("所选差异已经变化，请重新扫描后生成方案");
            }
            return issue;
        }).toList();
        if (selected.size() != request.issueKeys().size()) {
            throw new InvalidRequestException("方案不能重复选择同一项差异");
        }
        int executableCount = (int) selected.stream().filter(DatabaseContractIssueVO::executable).count();
        String planNo = "DBC-" + Func.simpleUuid().substring(0, 20).toUpperCase(java.util.Locale.ROOT);
        long planId = mapper.insertPlan(planNo, selected.size(), executableCount,
                request.summary().trim(), actor.loginName());
        for (DatabaseContractIssueVO issue : selected) {
            mapper.insertItem(planId, new DatabaseContractPlanItemSnapshot(
                    0, planId, issue.code(), issue.objectName(), issue.expected(), issue.actual(),
                    issue.direction(), issue.action(), issue.executable(), issue.ddlPreview(),
                    issue.executionNote(), "PENDING", "PENDING"));
        }
        audit(actor, "DATABASE_CONTRACT_PLAN_CREATED", planNo,
                "已生成数据库契约维护方案；差异" + selected.size() + "项，可执行" + executableCount + "项");
        return getPlan(planId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>强制创建人与审批人分离，且只有全部差异均可安全自动执行时才允许批准。</p>
     */
    @Transactional
    @Override
    public DatabaseContractPlanVO approve(long id, byte[] version, String note, AccessActor actor) {
        DatabaseContractPlanSnapshot current = requirePlan(id);
        if (current.createdBy().equalsIgnoreCase(actor.loginName())) {
            throw new ResourceConflictException("创建人不能审批自己的数据库契约方案");
        }
        if (current.executableCount() != current.issueCount()) {
            throw new ResourceConflictException("方案包含需要人工或代码处理的差异，不能批准在线执行");
        }
        if (mapper.approve(id, version, actor.loginName(), note.trim()) != 1) {
            throw new ResourceConflictException("方案状态或版本已经变化，请重新读取");
        }
        audit(actor, "DATABASE_CONTRACT_PLAN_APPROVED", current.planNo(), "数据库契约方案已批准");
        return getPlan(id);
    }

    /**
     * {@inheritDoc}
     *
     * <p>执行前重新扫描并逐项核对冻结差异，在单个事务内执行受控DDL和复验；失败时记录安全摘要。</p>
     */
    @Override
    public DatabaseContractPlanVO execute(long id, byte[] version, AccessActor actor) {
        DatabaseContractPlanSnapshot current = requirePlan(id);
        if (current.status() != DatabaseContractPlanStatus.APPROVED) {
            throw new ResourceConflictException("只有已批准方案可以执行");
        }
        try {
            transactionTemplate.executeWithoutResult(status -> executeTransaction(id, version, actor, current));
        } catch (RuntimeException exception) {
            mapper.markFailed(id, failureSummary(exception));
        }
        return getPlan(id);
    }

    /**
     * {@inheritDoc}
     *
     * <p>仅能取消尚未开始执行且行版本未变化的方案，并在同一事务追加审计。</p>
     */
    @Transactional
    @Override
    public DatabaseContractPlanVO cancel(long id, byte[] version, AccessActor actor) {
        DatabaseContractPlanSnapshot current = requirePlan(id);
        if (mapper.cancel(id, version) != 1) {
            throw new ResourceConflictException("方案已经开始执行或版本已变化，不能取消");
        }
        audit(actor, "DATABASE_CONTRACT_PLAN_CANCELLED", current.planNo(), "执行前已取消数据库契约方案");
        return getPlan(id);
    }

    /**
     * 在单个事务内重新核对差异、执行受控DDL并完成逐项复验。
     *
     * @param id 方案主键
     * @param version 并发版本
     * @param actor 执行人
     * @param current 执行前方案
     */
    private void executeTransaction(long id, byte[] version, AccessActor actor,
                                    DatabaseContractPlanSnapshot current) {
        if (mapper.markExecuting(id, version, actor.loginName()) != 1) {
            throw new ResourceConflictException("方案状态或版本已经变化，请重新读取");
        }
        List<DatabaseContractPlanItemSnapshot> items = mapper.findItems(id);
        DatabaseContractInspection before = inspectionService.inspect();
        Map<String, DatabaseContractIssueVO> liveIssues = issueMap(before);
        for (DatabaseContractPlanItemSnapshot item : items) {
            DatabaseContractIssueVO live = liveIssues.get(issueKey(item.violationCode(), item.objectName()));
            if (live == null || !live.executable()
                    || !item.expectedValue().equals(live.expected())
                    || !item.actualValue().equals(live.actual())
                    || !item.ddlPreview().equals(live.ddlPreview())) {
                throw new ResourceConflictException("差异已变化，DDL事务未执行");
            }
            jdbcTemplate.execute(item.ddlPreview());
        }
        mapper.markItemsExecuted(id);
        Map<String, DatabaseContractIssueVO> remaining = issueMap(inspectionService.inspect());
        boolean passed = true;
        for (DatabaseContractPlanItemSnapshot item : items) {
            boolean remains = remaining.containsKey(issueKey(item.violationCode(), item.objectName()));
            mapper.markItemVerification(item.id(), remains ? "REMAINS" : "PASSED");
            passed &= !remains;
        }
        if (!passed) {
            throw new ResourceConflictException("执行后复验仍存在差异，DDL事务已回滚");
        }
        mapper.markCompleted(id);
        audit(actor, "DATABASE_CONTRACT_PLAN_EXECUTED", current.planNo(),
                "受控DDL事务执行成功且重新扫描通过");
    }

    /**
     * 将内部契约检查结果转换为不包含数据库连接细节的API输出。
     *
     * @param inspection 检查结果
     * @return 页面扫描输出
     */
    private DatabaseContractInspectionVO toInspectionView(DatabaseContractInspection inspection) {
        List<DatabaseContractIssueVO> issues = buildIssues(inspection);
        return new DatabaseContractInspectionVO(LocalDateTime.now(), inspection.expectedColumnCount(),
                issues.size(), (int) issues.stream().filter(DatabaseContractIssueVO::executable).count(), issues);
    }

    /**
     * 把检查差异与可执行DDL规划合并为页面可处理的问题列表。
     *
     * @param inspection 检查结果
     * @return 差异输出
     */
    private List<DatabaseContractIssueVO> buildIssues(DatabaseContractInspection inspection) {
        List<DatabaseContractIssueVO> result = new ArrayList<>();
        for (DatabaseContractViolation violation : inspection.violations()) {
            var expected = inspection.expectedColumns().get(violation.objectName());
            var actual = inspection.actualColumns().get(violation.objectName());
            DatabaseContractRepairSql repair = repairPlanner.plan(violation, expected, actual);
            result.add(new DatabaseContractIssueVO(
                    issueKey(violation.code(), violation.objectName()), violation.code(),
                    violation.direction().name(), violation.objectName(), violation.expected(),
                    violation.actual(), violation.action(), repair.executable(), repair.sql(), repair.reason()));
        }
        return List.copyOf(result);
    }

    /**
     * 按稳定差异键索引检查结果，用于执行前后精确比对。
     *
     * @param inspection 检查结果
     * @return 以差异键索引的页面输出
     */
    private Map<String, DatabaseContractIssueVO> issueMap(DatabaseContractInspection inspection) {
        Map<String, DatabaseContractIssueVO> result = new LinkedHashMap<>();
        buildIssues(inspection).forEach(issue -> result.put(issue.issueKey(), issue));
        return result;
    }

    /**
     * 由差异代码和对象名生成稳定差异键。
     *
     * @param code 差异代码
     * @param objectName 对象名
     * @return 稳定差异键
     */
    private String issueKey(String code, String objectName) {
        return code + "::" + objectName;
    }

    /**
     * 读取维护方案；不存在时抛出资源不存在异常。
     *
     * @param id 主键
     * @return 存在的方案
     */
    private DatabaseContractPlanSnapshot requirePlan(long id) {
        DatabaseContractPlanSnapshot plan = mapper.findById(id);
        if (plan == null) {
            throw new ResourceNotFoundException("数据库契约维护方案不存在");
        }
        return plan;
    }

    /**
     * 将持久化快照和明细组装为数据库契约方案API输出。
     *
     * @param snapshot 方案快照
     * @return API输出
     */
    private DatabaseContractPlanVO toView(DatabaseContractPlanSnapshot snapshot) {
        List<DatabaseContractPlanItemVO> items = mapper.findItems(snapshot.id()).stream()
                .map(item -> new DatabaseContractPlanItemVO(
                        item.id(), item.violationCode(), item.objectName(), item.expectedValue(),
                        item.actualValue(), item.direction(), item.actionText(), item.executable(),
                        item.ddlPreview(), item.executionNote(), item.executionStatus(),
                        item.verificationStatus()))
                .toList();
        return new DatabaseContractPlanVO(
                snapshot.id(), snapshot.planNo(), snapshot.status(), snapshot.issueCount(),
                snapshot.executableCount(), snapshot.summary(), snapshot.createdBy(), snapshot.createdAt(),
                snapshot.approvedBy(), snapshot.approvedAt(), snapshot.approvalNote(), snapshot.executedBy(),
                snapshot.executedAt(), snapshot.verifiedAt(), snapshot.failureMessage(), snapshot.updatedAt(),
                Func.encodeBase64(snapshot.version()), items);
    }

    /**
     * 追加不包含DDL全文和连接信息的管理审计事件。
     *
     * @param actor 操作人
     * @param action 动作
     * @param planNo 方案编号
     * @param summary 摘要
     */
    private void audit(AccessActor actor, String action, String planNo, String summary) {
        auditService.recordSuccess(new ManagementAuditCommand(
                actor, null, null, null, action, "DATABASE_CONTRACT_PLAN", planNo,
                "SUCCESS", summary, auditService.currentRequestId()));
    }

    /**
     * 从执行异常生成不泄露SQL和连接细节的失败摘要。
     *
     * @param exception 执行异常
     * @return 不泄露SQL和连接细节的失败摘要
     */
    private String failureSummary(RuntimeException exception) {
        if (exception instanceof ResourceConflictException) {
            return exception.getMessage();
        }
        return "DDL执行失败，事务已自动回滚；请由DBA查看服务端日志和SQL Server错误记录";
    }
}
