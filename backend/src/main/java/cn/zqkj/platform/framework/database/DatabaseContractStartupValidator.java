package cn.zqkj.platform.framework.database;

import cn.zqkj.platform.framework.database.exception.DatabaseContractStartupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 在Flyway迁移和Spring上下文初始化后、应用进入Ready状态前执行数据库契约门禁。
 *
 * <p>本组件只读取并判断，不执行DDL。任何差异都会阻止实例进入Ready状态，开发人员或
 * DBA必须在应用外修复后重新启动。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseContractStartupValidator implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseContractStartupValidator.class);

    private final DatabaseContractInspectionService inspectionService;
    private final boolean enabled;

    /**
     * 创建启动期数据库契约门禁。
     *
     * @param inspectionService 统一只读检查服务
     * @param enabled 是否启用启动期强制检查
     */
    public DatabaseContractStartupValidator(
            DatabaseContractInspectionService inspectionService,
            @Value("${platform.database-contract.enabled:true}") boolean enabled) {
        this.inspectionService = inspectionService;
        this.enabled = enabled;
    }

    /**
     * 执行启动期强制检查；存在差异时抛出异常阻止应用进入Ready状态。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            LOGGER.warn("[DB-CONTRACT] 启动门禁已关闭；当前实例不能作为结构兼容性验证证据");
            return;
        }
        LOGGER.info("[DB-CONTRACT] 启动门禁开始：只读检查数据库、Flyway契约、Mapper和Java模型");
        DatabaseContractInspection inspection;
        try {
            inspection = inspectionService.inspect();
        } catch (RuntimeException exception) {
            LOGGER.error("[DB-CONTRACT][DBCONTRACT-E000] 检查过程异常，实例拒绝进入Ready状态；"
                    + "请修复连接、权限或契约解析问题后重新启动", exception);
            throw new DatabaseContractStartupException(
                    "数据库契约检查过程异常，实例未启动；请查看DBCONTRACT-E000日志", exception);
        }
        if (!inspection.passed()) {
            logFailure(inspection);
            throw new DatabaseContractStartupException(
                    "数据库契约检查失败，共发现 " + inspection.violations().size()
                            + " 项差异；实例未启动，请离线修复后重试",
                    inspection.violations());
        }
        LOGGER.info("[DB-CONTRACT] PASSED：SQL Server 2012/兼容级别110、{}个字段及全部构造映射一致",
                inspection.expectedColumnCount());
    }

    /**
     * 输出可直接定位修改方向的失败报告和离线修复入口。
     *
     * @param inspection 未通过的检查结果
     */
    private void logFailure(DatabaseContractInspection inspection) {
        LOGGER.error("[DB-CONTRACT] FAILED：共{}项；改数据库{}项，改Mapper/模型{}项，人工判断{}项",
                inspection.violations().size(),
                inspection.count(DatabaseContractViolation.Direction.DATABASE),
                inspection.count(DatabaseContractViolation.Direction.MAPPER_OR_MODEL),
                inspection.count(DatabaseContractViolation.Direction.MANUAL_REVIEW));
        for (DatabaseContractViolation violation : inspection.violations()) {
            LOGGER.error("[DB-CONTRACT] {}", violation.format());
        }
        LOGGER.error("[DB-CONTRACT] 实例不会进入Ready状态，也不会开放在线修复；"
                + "数据库差异先执行deploy/sqlserver/04-verify-database-contract.sql确认，"
                + "再评审并执行05-repair-database-contract.sql或正式Flyway迁移；"
                + "Mapper/模型差异修改代码。修复后必须重新启动并通过本门禁");
    }
}
