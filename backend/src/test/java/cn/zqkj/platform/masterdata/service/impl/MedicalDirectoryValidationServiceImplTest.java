package cn.zqkj.platform.masterdata.service.impl;

import cn.zqkj.platform.his.domain.model.MedicalDirectoryEntry;
import cn.zqkj.platform.his.domain.model.MedicalDirectoryType;
import cn.zqkj.platform.masterdata.domain.model.MedicalDirectorySourceRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证医院三大目录自动校验不会放过漏页、冲突或必填缺失。 */
class MedicalDirectoryValidationServiceImplTest {

    /** 验证同一稳定编码的相同行会归并，但不同内容会阻断发布。 */
    @Test
    void rejectsConflictAndAcceptsExactDuplicate() {
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
                new MedicalDirectoryEntry(code, name, "AMXL", "西药", "盒", "0.25g", "胶囊", null,
                        null, "2026-09-20 00:00:00", null, null, null, null, null, null,
                        null, null, enabled));
    }
}
