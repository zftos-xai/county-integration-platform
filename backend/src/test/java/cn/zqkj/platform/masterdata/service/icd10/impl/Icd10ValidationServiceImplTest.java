package cn.zqkj.platform.masterdata.service.icd10.impl;

import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10DiagnosisCategory;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10SourceEntry;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10SourceRecord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证公共ICD10来源记录在持久化前的最小质量边界。 */
class Icd10ValidationServiceImplTest {

    /** 相同类别和编码的完全相同事实会阻止整类写入，不能被当作可忽略的重复。 */
    @Test
    void rejectsDuplicateSourceEntryWithinOneDiagnosisCategory() {
        var service = new Icd10ValidationServiceImpl();
        Icd10SourceRecord entry = record(Icd10DiagnosisCategory.WESTERN, "I10", "高血压病", "DX-1");

        var result = service.validate(2, java.util.List.of(entry, entry));

        assertFalse(result.valid());
        assertEquals(1, result.duplicateCount());
        assertEquals(1, result.acceptedRecords().size());
    }

    /** 相同编码在不同诊断类别内只作为本次响应的独立事实，不预先假定跨类别唯一。 */
    @Test
    void keepsSameCodeFromSeparateDiagnosisCategoriesDistinctForResponseValidation() {
        var service = new Icd10ValidationServiceImpl();

        var result = service.validate(2, java.util.List.of(
                record(Icd10DiagnosisCategory.WESTERN, "A01", "西医名称", "DX-1"),
                record(Icd10DiagnosisCategory.TRADITIONAL, "A01", "中医名称", "DX-2")));

        assertTrue(result.valid());
        assertEquals(2, result.acceptedRecords().size());
    }

    /** 来源疾病ID为空不阻止协议已确认字段完整的记录进入后续流程。 */
    @Test
    void acceptsMissingOptionalSourceDiseaseId() {
        var service = new Icd10ValidationServiceImpl();

        var result = service.validate(1, java.util.List.of(
                record(Icd10DiagnosisCategory.WESTERN, "I10", "高血压病", null)));

        assertTrue(result.valid());
    }

    /** 同一疾病编码但不同来源疾病ID是两个独立目录实体，必须一并保留。 */
    @Test
    void acceptsSameDiseaseCodeWithDistinctSourceDiseaseIds() {
        var service = new Icd10ValidationServiceImpl();

        var result = service.validate(2, java.util.List.of(
                record(Icd10DiagnosisCategory.TRADITIONAL, "BRE001", "中医名称甲", "DX-1"),
                record(Icd10DiagnosisCategory.TRADITIONAL, "BRE001", "中医名称乙", "DX-2")));

        assertTrue(result.valid());
        assertEquals(2, result.acceptedRecords().size());
    }

    /** 来源疾病ID相同却返回不同事实时，不能在不明确来源语义的情况下覆盖任一记录。 */
    @Test
    void rejectsConflictingFactsWithSameSourceDiseaseId() {
        var service = new Icd10ValidationServiceImpl();

        var result = service.validate(2, java.util.List.of(
                record(Icd10DiagnosisCategory.WESTERN, "A01", "名称甲", "DX-1"),
                record(Icd10DiagnosisCategory.WESTERN, "A02", "名称乙", "DX-1")));

        assertFalse(result.valid());
        assertEquals(1, result.conflictCount());
        assertTrue(result.acceptedRecords().isEmpty());
    }

    /** 原始西医响应中最长助记码为52字符，64字符范围内应完整保留。 */
    @Test
    void acceptsMnemonicCodeLongerThanTwentyCharacters() {
        var service = new Icd10ValidationServiceImpl();
        Icd10SourceRecord entry = new Icd10SourceRecord(Icd10DiagnosisCategory.WESTERN,
                new Icd10SourceEntry("I10", "高血压病", "A".repeat(52), null,
                        "2019-01-01 08:00:00", "DX-1"));

        var result = service.validate(1, java.util.List.of(entry));

        assertTrue(result.valid());
    }

    /** 原始西医响应中存在53字符病种名称，64字符范围内应完整保留。 */
    @Test
    void acceptsDiseaseNameLongerThanFiftyCharacters() {
        var service = new Icd10ValidationServiceImpl();
        Icd10SourceRecord entry = new Icd10SourceRecord(Icd10DiagnosisCategory.WESTERN,
                new Icd10SourceEntry("I10", "名".repeat(53), "ZDF", null,
                        "2019-01-01 08:00:00", "DX-1"));

        var result = service.validate(1, java.util.List.of(entry));

        assertTrue(result.valid());
    }

    /** 创建不包含真实患者或真实机构资料的来源诊断测试记录。 */
    private Icd10SourceRecord record(
            Icd10DiagnosisCategory category, String code, String name, String sourceDiseaseId
    ) {
        return new Icd10SourceRecord(category,
                new Icd10SourceEntry(code, name, "ZDF", null, "2019-01-01 08:00:00", sourceDiseaseId));
    }
}
