package cn.zqkj.platform.databasecontract.service;

import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.databasecontract.domain.model.DatabaseContractPlanSnapshot;
import cn.zqkj.platform.databasecontract.domain.model.DatabaseContractPlanStatus;
import cn.zqkj.platform.databasecontract.mapper.DatabaseContractPlanMapper;
import cn.zqkj.platform.databasecontract.service.impl.DatabaseContractMaintenanceServiceImpl;
import cn.zqkj.platform.framework.database.DatabaseContractInspectionService;
import cn.zqkj.platform.framework.database.DatabaseContractRepairPlanner;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证数据库契约维护服务的双人审批和全项可执行边界。 */
class DatabaseContractMaintenanceServiceTest {

    /** 原差异消失但同字段新增精度差异时，必须回滚而非宣告完成。 */
    @Test
    void rejectsNewViolationAfterExecutingDdl() {
        var mapper = mock(DatabaseContractPlanMapper.class);
        var inspection = mock(DatabaseContractInspectionService.class);
        var planner = mock(DatabaseContractRepairPlanner.class);
        var jdbc = mock(JdbcTemplate.class);
        var transaction = mock(TransactionTemplate.class);
        String object = "dbo.t.amount";
        String ddl = "ALTER TABLE [dbo].[t] ALTER COLUMN [amount] DECIMAL(18,4) NULL;";
        var before = new cn.zqkj.platform.framework.database.DatabaseContractViolation(
                "DBCONTRACT-E106", cn.zqkj.platform.framework.database.DatabaseContractViolation.Direction.DATABASE,
                object, "NULL", "NOT NULL", "repair");
        var after = new cn.zqkj.platform.framework.database.DatabaseContractViolation(
                "DBCONTRACT-E105", cn.zqkj.platform.framework.database.DatabaseContractViolation.Direction.DATABASE,
                object, "4", "0", "repair");
        when(mapper.findById(25L)).thenReturn(approvedPlan());
        when(mapper.markExecuting(anyLong(), any(), any())).thenReturn(1);
        when(mapper.findItems(25L)).thenReturn(List.of(
                new cn.zqkj.platform.databasecontract.domain.model.DatabaseContractPlanItemSnapshot(
                        1L, 25L, before.code(), object, "NULL", "NOT NULL", "DATABASE", "repair",
                        true, ddl, "safe", "PENDING", "PENDING")));
        when(inspection.inspect()).thenReturn(
                new cn.zqkj.platform.framework.database.DatabaseContractInspection(1, List.of(before)),
                new cn.zqkj.platform.framework.database.DatabaseContractInspection(1, List.of(after)));
        when(planner.plan(any(), any(), any())).thenReturn(
                new cn.zqkj.platform.framework.database.DatabaseContractRepairSql(true, ddl, "safe"));
        doAnswer(invocation -> {
            invocation.getArgument(0, Consumer.class).accept(mock(TransactionStatus.class));
            return null;
        }).when(transaction).executeWithoutResult(any());
        var service = new DatabaseContractMaintenanceServiceImpl(inspection, planner, mapper, jdbc, transaction,
                mock(ManagementAuditService.class));
        assertThrows(ResourceConflictException.class, () -> service.execute(25L, new byte[8], actor("operator")));
        verify(jdbc).execute(ddl);
        verify(mapper).markItemVerification(1L, "REMAINS");
        verify(mapper, never()).markCompleted(anyLong());
    }

    /** 创建人即使具备审批权限也不能批准自己的方案。 */
    @Test
    void rejectsApprovalByPlanCreator() {
        DatabaseContractPlanMapper mapper = mock(DatabaseContractPlanMapper.class);
        when(mapper.findById(25L)).thenReturn(plan("creator", 1, 1));
        DatabaseContractMaintenanceService service = service(mapper);

        assertThrows(ResourceConflictException.class,
                () -> service.approve(25L, new byte[8], "已核对", actor("creator")));

        verify(mapper, never()).approve(anyLong(), any(), any(), any());
    }

    /** 包含代码修改或人工判断项的方案不能被批准进入在线DDL执行。 */
    @Test
    void rejectsApprovalWhenAnyIssueIsNotExecutable() {
        DatabaseContractPlanMapper mapper = mock(DatabaseContractPlanMapper.class);
        when(mapper.findById(25L)).thenReturn(plan("creator", 2, 1));
        DatabaseContractMaintenanceService service = service(mapper);

        assertThrows(ResourceConflictException.class,
                () -> service.approve(25L, new byte[8], "已核对", actor("reviewer")));

        verify(mapper, never()).approve(anyLong(), any(), any(), any());
    }

    /** 并发版本失效时不得把其他执行者的方案改写为失败。 */
    @Test
    void rejectsStaleExecutionWithoutMarkingPlanFailed() {
        DatabaseContractPlanMapper mapper = mock(DatabaseContractPlanMapper.class);
        when(mapper.findById(25L)).thenReturn(approvedPlan());
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transaction).executeWithoutResult(any());
        DatabaseContractMaintenanceService service = service(mapper, transaction);
        byte[] version = new byte[8];

        assertThrows(ResourceConflictException.class,
                () -> service.execute(25L, version, actor("operator")));

        verify(mapper).markFailed(25L, version, "方案状态或版本已经变化，请重新读取");
    }

    /** 同版本真实执行异常只记录受控摘要，不泄露底层SQL错误信息。 */
    @Test
    void recordsSanitizedFailureForCurrentApprovedVersion() {
        DatabaseContractPlanMapper mapper = mock(DatabaseContractPlanMapper.class);
        when(mapper.findById(25L)).thenReturn(approvedPlan());
        when(mapper.findItems(25L)).thenReturn(List.of());
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            throw new IllegalStateException("secret SQL details");
        }).when(transaction).executeWithoutResult(any());
        byte[] version = new byte[8];
        String safeMessage = "DDL执行失败，事务已自动回滚；请由DBA查看服务端日志和SQL Server错误记录";
        when(mapper.markFailed(25L, version, safeMessage)).thenReturn(1);

        assertDoesNotThrow(() -> service(mapper, transaction).execute(25L, version, actor("operator")));

        verify(mapper).markFailed(25L, version, safeMessage);
    }

    /**
     * 创建被测服务并注入测试替身。
     *
     * @param mapper 方案持久化模拟
     * @return 待测试服务
     */
    private DatabaseContractMaintenanceService service(DatabaseContractPlanMapper mapper) {
        return service(mapper, mock(TransactionTemplate.class));
    }

    /**
     * 使用指定事务替身创建被测服务。
     *
     * @param mapper 方案持久化模拟
     * @param transaction 事务执行模拟
     * @return 待测试服务
     */
    private DatabaseContractMaintenanceService service(DatabaseContractPlanMapper mapper,
                                                       TransactionTemplate transaction) {
        return new DatabaseContractMaintenanceServiceImpl(
                mock(DatabaseContractInspectionService.class),
                mock(DatabaseContractRepairPlanner.class),
                mapper,
                mock(JdbcTemplate.class),
                transaction,
                mock(ManagementAuditService.class));
    }

    /**
     * 创建指定状态和执行数量的数据库契约测试方案。
     *
     * @param creator 创建人
     * @param issueCount 差异数
     * @param executableCount 可执行数
     * @return 方案快照
     */
    private DatabaseContractPlanSnapshot plan(String creator, int issueCount, int executableCount) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 19, 10, 0);
        return new DatabaseContractPlanSnapshot(
                25L, "DBC-TEST", DatabaseContractPlanStatus.DRAFT, issueCount, executableCount,
                "测试方案", creator, now, null, null, null, null, null, null, null, now, new byte[8]);
    }

    /**
     * 创建可尝试执行的已批准方案。
     *
     * @return 已批准方案快照
     */
    private DatabaseContractPlanSnapshot approvedPlan() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 19, 10, 0);
        return new DatabaseContractPlanSnapshot(
                25L, "DBC-TEST", DatabaseContractPlanStatus.APPROVED, 1, 1,
                "测试方案", "creator", now, "reviewer", now, "已核对", null, null, null, null, now, new byte[8]);
    }

    /**
     * 创建具有测试机构范围的服务层操作人。
     *
     * @param loginName 登录名
     * @return 操作人
     */
    private AccessActor actor(String loginName) {
        return new AccessActor(1L, loginName, Set.of("ORG001"));
    }
}
