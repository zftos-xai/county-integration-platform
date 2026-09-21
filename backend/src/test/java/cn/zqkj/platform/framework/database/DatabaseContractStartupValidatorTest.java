package cn.zqkj.platform.framework.database;

import cn.zqkj.platform.framework.database.exception.DatabaseContractStartupException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证数据库契约差异会在应用进入Ready状态前阻止启动。 */
class DatabaseContractStartupValidatorTest {

    /** 验证确定性字段差异最终转换为包含差异数量的启动失败。 */
    @Test
    void failsStartupWhenDatabaseContractDiffers() {
        DatabaseContractInspectionService inspectionService = mock(DatabaseContractInspectionService.class);
        when(inspectionService.inspect()).thenReturn(new DatabaseContractInspection(27, List.of(
                new DatabaseContractViolation(
                        "DBCONTRACT-E103",
                        DatabaseContractViolation.Direction.DATABASE,
                        "dbo.md_hospital_directory.source_record_name",
                        "nvarchar(50)",
                        "nvarchar(20)",
                        "通过正式迁移修正数据库长度"
                )
        )));
        DatabaseContractStartupValidator validator = new DatabaseContractStartupValidator(inspectionService, true);

        DatabaseContractStartupException exception = assertThrows(DatabaseContractStartupException.class,
                () -> validator.run(mock(ApplicationArguments.class)));

        assertTrue(exception.getMessage().contains("1 项差异"));
        assertTrue(exception.getMessage().contains("实例未启动"));
        assertTrue(exception.violations().get(0).objectName().contains("source_record_name"));
    }

    /** 验证检查过程自身失败时同样拒绝实例进入Ready状态并保留原始原因。 */
    @Test
    void failsStartupWhenInspectionCannotComplete() {
        DatabaseContractInspectionService inspectionService = mock(DatabaseContractInspectionService.class);
        when(inspectionService.inspect()).thenThrow(new IllegalStateException("无法读取系统目录"));
        DatabaseContractStartupValidator validator = new DatabaseContractStartupValidator(inspectionService, true);

        DatabaseContractStartupException exception = assertThrows(DatabaseContractStartupException.class,
                () -> validator.run(mock(ApplicationArguments.class)));

        assertTrue(exception.getMessage().contains("DBCONTRACT-E000"));
        assertTrue(exception.getCause().getMessage().contains("系统目录"));
    }
}
