package cn.zqkj.platform.framework.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证在线DDL规划器只放行明确且可回滚的低风险结构变化。 */
class DatabaseContractRepairPlannerTest {

    private final DatabaseContractRepairPlanner planner = new DatabaseContractRepairPlanner();

    /** 验证nvarchar长度按字符生成且只允许扩大。 */
    @Test
    void generatesSqlForNvarcharExpansion() {
        DatabaseContractColumn expected = column(200, false);
        DatabaseContractColumn actual = column(100, false);
        DatabaseContractViolation violation = violation("DBCONTRACT-E103");

        DatabaseContractRepairSql result = planner.plan(violation, expected, actual);

        assertTrue(result.executable());
        assertEquals("ALTER TABLE [dbo].[md_hospital_directory] ALTER COLUMN [source_record_name]"
                + " NVARCHAR(100) NOT NULL;", result.sql());
    }

    /** 验证缩短字段不会进入在线DDL执行。 */
    @Test
    void rejectsLengthReduction() {
        DatabaseContractRepairSql result = planner.plan(
                violation("DBCONTRACT-E103"), column(100, false), column(200, false));

        assertFalse(result.executable());
        assertTrue(result.reason().contains("缩短"));
    }

    /** 验证收紧NOT NULL不会绕过存量数据评审。 */
    @Test
    void rejectsNullabilityTightening() {
        DatabaseContractRepairSql result = planner.plan(
                violation("DBCONTRACT-E106"), column(100, false), column(100, true));

        assertFalse(result.executable());
    }

    /**
     * 创建SQL Server字段契约测试数据。
     *
     * @param length SQL Server字节长度
     * @param nullable 是否允许NULL
     * @return 测试字段
     */
    private DatabaseContractColumn column(int length, boolean nullable) {
        return new DatabaseContractColumn(
                "dbo", "md_hospital_directory", "source_record_name", "nvarchar",
                length, null, null, nullable, false, false);
    }

    /**
     * 创建数据库契约差异测试数据。
     *
     * @param code 差异代码
     * @return 测试差异
     */
    private DatabaseContractViolation violation(String code) {
        return new DatabaseContractViolation(
                code, DatabaseContractViolation.Direction.DATABASE,
                "dbo.md_hospital_directory.source_record_name", "expected", "actual", "repair");
    }
}
