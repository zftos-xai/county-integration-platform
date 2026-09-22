package cn.zqkj.platform.masterdata.service.medicaldirectory.impl;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceEntry;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证医院三大目录自动校验不会放过漏页、冲突或必填缺失。 */
class MedicalDirectoryValidationServiceImplTest {

    /** 大量冲突只产生定长统计摘要，不包含来源编码或超过数据库字段上限。 */
    @Test
    void boundsConflictSummaryWithoutEchoingSourceData() {
        var records = new java.util.ArrayList<MedicalDirectorySourceRecord>();
        for (int index = 0; index < 200; index++) {
            records.add(record("SENSITIVE-" + index, "A", "true"));
            records.add(record("SENSITIVE-" + index, "B", "true"));
        }
        var result = new MedicalDirectoryValidationServiceImpl().validate(400, records);
        assertEquals(200, result.conflictCount());
        assertFalse(result.failureSummary().contains("SENSITIVE"));
        assertTrue(result.failureSummary().length() <= 500);
    }

    /** 同编码重复与冲突分别计数，均不能掩盖缺失记录而通过完整性校验。 */
    @Test
    void countsDuplicateAndConflictingRowsSeparately() {
        var service = new MedicalDirectoryValidationServiceImpl();
        var valid = record("M001", "阿莫西林", "true");
        var duplicate = record("M001", "阿莫西林", "true");
        var conflict = record("M001", "阿莫西林胶囊", "true");

        var result = service.validate(3, List.of(valid, duplicate, conflict));

        assertFalse(result.valid());
        assertEquals(1, result.duplicateCount());
        assertEquals(1, result.conflictCount());
        assertEquals(0, result.acceptedRecords().size());
    }

    /** 首尾空白不改变来源业务事实，比较双方必须使用同样规范化后的记录。 */
    @Test
    void rejectsDuplicatesThatCouldHideMissingRows() {
        var service = new MedicalDirectoryValidationServiceImpl();
        var result = service.validate(2, List.of(
                record(" M001 ", " 阿莫西林 ", "true"),
                record("M001", "阿莫西林", "true")));

        assertFalse(result.valid());
        assertEquals(1, result.duplicateCount());
        assertEquals(0, result.conflictCount());
        assertEquals(1, result.acceptedRecords().size());
    }

    /** 验证声明数量不一致和来源启用值缺失都会阻止后续发布。 */
    @Test
    void rejectsCountMismatchAndMissingRequiredFields() {
        var service = new MedicalDirectoryValidationServiceImpl();
        var invalid = record("M002", "布洛芬", null);

        var result = service.validate(2, List.of(invalid));

        assertFalse(result.valid());
        assertEquals(1, result.invalidCount());
        assertTrue(result.failureSummary().contains("来源声明2条"));
    }

    /**
     * 创建字段满足当前测试条件的来源记录。
     *
     * @param code 目录编码
     * @param name 目录名称
     * @param enabled 来源启用原始值
     * @return 合成目录记录
     */
    private MedicalDirectorySourceRecord record(String code, String name, String enabled) {
        return new MedicalDirectorySourceRecord(MedicalDirectoryType.WESTERN_MEDICINE,
                new MedicalDirectorySourceEntry(code, name, "AMXL", "西药", "盒", "0.25g", "胶囊", null,
                        null, "2026-09-20 00:00:00", null, null, null, null, null, null,
                        null, null, enabled));
    }
}
