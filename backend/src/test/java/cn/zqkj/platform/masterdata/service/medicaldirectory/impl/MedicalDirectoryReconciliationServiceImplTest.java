package cn.zqkj.platform.masterdata.service.medicaldirectory.impl;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceEntry;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证只有完整可确认的HIS范围才会产生缺失失效动作。 */
class MedicalDirectoryReconciliationServiceImplTest {

    /** 验证完整有效范围会将当前但未返回的编码标记为待失效。 */
    @Test
    void invalidatesOnlyCodesMissingFromCompleteSourceRange() {
        var service = new MedicalDirectoryReconciliationServiceImpl();
        var result = service.reconcile(validResult(), Set.of("A001", "A002", "A003"));

        assertEquals(2, result.recordsToUpsert().size());
        assertEquals(Set.of("A003"), result.codesToInvalidate());
    }

    /** 验证缺字段或数量不一致时禁止把本地缺失误判为HIS删除。 */
    @Test
    void neverInvalidatesWhenSourceRangeIsNotFullyValid() {
        var service = new MedicalDirectoryReconciliationServiceImpl();
        var incomplete = new MedicalDirectoryValidationResult(
                3, 2, 0, 0, 0, records(), "来源声明3条，分页实际取得2条");

        var result = service.reconcile(incomplete, Set.of("A001", "A002", "A003"));

        assertEquals(2, result.recordsToUpsert().size());
        assertTrue(result.codesToInvalidate().isEmpty());
    }

    /**
     * 创建两条可安全写入的医疗目录记录。
     *
     * @return 两条可写入的来源记录
     */
    private List<MedicalDirectorySourceRecord> records() {
        return List.of(record("A001"), record("A002"));
    }

    /**
     * 创建声明数量、实际数量和来源记录一致的校验结果。
     *
     * @return 声明数、实际数和来源记录一致的校验结论
     */
    private MedicalDirectoryValidationResult validResult() {
        return new MedicalDirectoryValidationResult(2, 2, 0, 0, 0, records(), null);
    }

    /**
     * 创建字段满足当前测试条件的来源记录。
     *
     * @param code 来源稳定编码
     * @return 合成有效来源记录
     */
    private MedicalDirectorySourceRecord record(String code) {
        return new MedicalDirectorySourceRecord(MedicalDirectoryType.CONSUMABLE,
                new MedicalDirectorySourceEntry(code, "耗材" + code, null, "耗材", null, null, null,
                        null, null, "2026-09-20 00:00:00", null, null, null, null, null,
                        null, null, null, "1"));
    }
}
