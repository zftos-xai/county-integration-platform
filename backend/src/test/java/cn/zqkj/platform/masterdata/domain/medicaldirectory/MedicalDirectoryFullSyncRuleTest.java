package cn.zqkj.platform.masterdata.domain.medicaldirectory;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryFullSyncRule;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证全量范围只来自绑定端点且具有确认依据的规则。 */
class MedicalDirectoryFullSyncRuleTest {

    /** 接受绑定端点、带时区下界与完整确认依据的规则。 */
    @Test
    void acceptsConfirmedRuleWithExplicitOffset() {
        MedicalDirectoryFullSyncRule rule = MedicalDirectoryFullSyncRule.parse("""
                {"endpointId":9,"rangeStart":"2000-01-01T00:00:00+08:00",
                 "evidence":"来源方确认四类目录的时间过滤字段、空值与最早记录覆盖口径"}
                """);
        assertEquals(9L, rule.endpointId());
        assertEquals(OffsetDateTime.parse("2000-01-01T00:00:00+08:00"), rule.rangeStart());
    }

    /** 缺少明确时间偏移或确认依据时不能启用全量模式。 */
    @Test
    void rejectsRuleWithoutEvidenceOrOffset() {
        assertThrows(IllegalArgumentException.class, () -> MedicalDirectoryFullSyncRule.parse(
                "{\"endpointId\":9,\"rangeStart\":\"2000-01-01\",\"evidence\":\"未确认\"}"));
        assertThrows(IllegalArgumentException.class, () -> MedicalDirectoryFullSyncRule.parse(
                "{\"endpointId\":9,\"rangeStart\":\"2000-01-01T00:00:00+08:00\",\"evidence\":\"未确认\"}"));
    }
}
