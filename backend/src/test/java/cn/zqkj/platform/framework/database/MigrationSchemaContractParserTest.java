package cn.zqkj.platform.framework.database;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证Flyway迁移可以稳定生成启动期数据库字段契约。 */
class MigrationSchemaContractParserTest {

    /** 验证字符长度、时间精度、空值和rowversion规则均能从真实迁移中提取。 */
    @Test
    void parsesCurrentMigrationColumnContracts() {
        MigrationSchemaContractParser parser = new MigrationSchemaContractParser(
                new PathMatchingResourcePatternResolver());

        Map<String, DatabaseContractColumn> columns = parser.parse();

        DatabaseContractColumn directoryName = columns.get("dbo.md_hospital_directory.source_record_name");
        assertEquals("nvarchar", directoryName.typeName());
        assertEquals(100, directoryName.maxLength());
        assertFalse(directoryName.nullable());

        DatabaseContractColumn updatedAt = columns.get("dbo.md_hospital_directory.updated_at");
        assertEquals("datetime2", updatedAt.typeName());
        assertEquals(3, updatedAt.scale());
        assertFalse(updatedAt.nullable());

        DatabaseContractColumn rowVersion = columns.get("dbo.md_hospital_directory.row_version");
        assertEquals("rowversion", rowVersion.typeName());
        assertFalse(rowVersion.nullable());
        assertTrue(columns.size() > 100, "应覆盖全部现有业务表字段，而不是只检查少量样例");
    }
}
