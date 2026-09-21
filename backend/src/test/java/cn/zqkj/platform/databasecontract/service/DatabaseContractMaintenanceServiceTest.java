package cn.zqkj.platform.databasecontract.service;

import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.databasecontract.domain.model.DatabaseContractPlanSnapshot;
import cn.zqkj.platform.databasecontract.domain.model.DatabaseContractPlanStatus;
import cn.zqkj.platform.databasecontract.mapper.DatabaseContractPlanMapper;
import cn.zqkj.platform.databasecontract.service.impl.DatabaseContractMaintenanceServiceImpl;
import cn.zqkj.platform.framework.database.DatabaseContractInspectionService;
import cn.zqkj.platform.framework.database.DatabaseContractRepairPlanner;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.service.ManagementAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证数据库契约维护服务的双人审批和全项可执行边界。 */
class DatabaseContractMaintenanceServiceTest {

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

    /**
     * 创建被测服务并注入测试替身。
     *
     * @param mapper 方案持久化模拟
     * @return 待测试服务
     */
    private DatabaseContractMaintenanceService service(DatabaseContractPlanMapper mapper) {
        return new DatabaseContractMaintenanceServiceImpl(
                mock(DatabaseContractInspectionService.class),
                mock(DatabaseContractRepairPlanner.class),
                mapper,
                mock(JdbcTemplate.class),
                mock(TransactionTemplate.class),
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
     * 创建具有测试机构范围的服务层操作人。
     *
     * @param loginName 登录名
     * @return 操作人
     */
    private AccessActor actor(String loginName) {
        return new AccessActor(1L, loginName, Set.of("ORG001"));
    }
}
