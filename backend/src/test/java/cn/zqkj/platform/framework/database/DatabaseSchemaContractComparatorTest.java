package cn.zqkj.platform.framework.database;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证数据库字段差异能够给出明确修改方向和可读报告。 */
class DatabaseSchemaContractComparatorTest {

    /** 验证nvarchar长度缩短会被归类为数据库侧问题并显示字符及字节长度。 */
    @Test
    void reportsDatabaseDirectionWhenTextLengthDiffers() {
        DatabaseContractColumn expected = column(80);
        DatabaseContractColumn actual = column(40);

        List<DatabaseContractViolation> violations = new DatabaseSchemaContractComparator().compare(
                Map.of(expected.qualifiedName(), expected),
                Map.of(actual.qualifiedName(), actual));

        assertEquals(1, violations.size());
        DatabaseContractViolation violation = violations.get(0);
        assertEquals("DBCONTRACT-E103", violation.code());
        assertEquals(DatabaseContractViolation.Direction.DATABASE, violation.direction());
        assertTrue(violation.format().contains("40字符/80字节"));
        assertTrue(violation.format().contains("20字符/40字节"));
        assertTrue(violation.action().contains("正式迁移"));
    }

    /**
     * 创建测试使用的nvarchar字段。
     *
     * @param maxLength SQL Server系统目录中的最大字节数
     * @return 测试字段
     */
    private DatabaseContractColumn column(int maxLength) {
        return new DatabaseContractColumn(
                "dbo", "md_hospital_directory", "source_record_name", "nvarchar",
                maxLength, null, null, false, false, false);
    }
}
