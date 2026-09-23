package cn.zqkj.platform.his.service;

import cn.zqkj.platform.his.domain.icd10.dto.Icd10CountQuery;
import cn.zqkj.platform.his.domain.icd10.dto.Icd10Query;
import cn.zqkj.platform.his.domain.icd10.model.Icd10DiagnosisCategory;
import cn.zqkj.platform.his.domain.icd10.model.Icd10Entry;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.masterdata.mapper.batch.MasterDataBatchMapper;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用已保存测试端点执行只读ICD10声明数量探测，不创建或更新同步批次。 */
@SpringBootTest
@EnabledIfSystemProperty(named = "liveHisProbe", matches = "true")
class Icd10LiveProbeTest {

    private static final String ORGANIZATION_CODE = "8BCDCA4E11A44A9D888D7E70DD4FF44F";
    private static final LocalDateTime RANGE_START = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime RANGE_END = LocalDateTime.of(2099, 12, 31, 23, 59, 59);
    private final PhisService phisService;
    private final MasterDataBatchMapper batchMapper;

    /**
     * 创建只读ICD10联调探测。
     *
     * @param phisService HIS调用边界
     * @param batchMapper 已授权测试机构的主键读取边界
     */
    @Autowired
    Icd10LiveProbeTest(PhisService phisService, MasterDataBatchMapper batchMapper) {
        this.phisService = phisService;
        this.batchMapper = batchMapper;
    }

    /** 验证测试端点可对西医和中医类别返回明确、非负的100-007声明数量。 */
    @Test
    void readsDeclaredCountsForBothDiagnosisCategories() {
        Long organizationId = batchMapper.findEnabledOrganizationId(ORGANIZATION_CODE);
        assertNotNull(organizationId);
        for (Icd10DiagnosisCategory category : Icd10DiagnosisCategory.values()) {
            PhisResponse<Long> response = phisService.countIcd10(
                    organizationId,
                    ParameterEnvironment.TEST,
                    new Icd10CountQuery(null, RANGE_START, RANGE_END, category, null));
            assertTrue(response.success(), category.displayName() + "诊断目录行数查询失败：" + response.errorMessage());
            assertNotNull(response.data());
            assertTrue(response.data() >= 0, category.displayName() + "诊断目录行数不能为负数");
            System.out.println("100-007 " + category.displayName() + "声明行数=" + response.data());
        }
    }

    /**
     * 只读取得完整响应并输出可脱敏复核的字段质量、重复和冲突样本。
     *
     * <p>本探测不创建同步批次，也不保存任何来源记录；样本只包含疾病编码和字段长度，
     * 用于区分HIS源数据问题与平台映射问题。</p>
     */
    @Test
    void reportsIcd10ValidationSamplesWithoutPersistingSourceRecords() {
        Long organizationId = batchMapper.findEnabledOrganizationId(ORGANIZATION_CODE);
        assertNotNull(organizationId);
        for (Icd10DiagnosisCategory category : Icd10DiagnosisCategory.values()) {
            List<Icd10Entry> entries = readAllEntries(organizationId, category);
            List<String> invalidSamples = new ArrayList<>();
            Map<String, Set<Icd10Entry>> factsByCode = new LinkedHashMap<>();
            for (Icd10Entry entry : entries) {
                String invalidReason = invalidReason(entry);
                if (invalidReason != null && invalidSamples.size() < 5) {
                    invalidSamples.add(sample(entry) + "，原因=" + invalidReason);
                }
                if (invalidReason == null) {
                    factsByCode.computeIfAbsent(entry.diseaseCode().trim(), ignored -> new LinkedHashSet<>()).add(entry);
                }
            }
            List<String> conflictSamples = factsByCode.entrySet().stream()
                    .filter(entry -> entry.getValue().size() > 1)
                    .limit(5)
                    .map(entry -> "编码=" + entry.getKey() + "，不一致事实=" + entry.getValue().size())
                    .toList();
            System.out.println("100-006 " + category.displayName() + "总行=" + entries.size()
                    + "；无效样本=" + invalidSamples + "；冲突样本=" + conflictSamples);
        }
    }

    /**
     * 只读输出中医同编码多记录在目录字段与追溯字段上的差异维度。
     *
     * <p>不输出名称、备注、创建时间或来源ID的正文，避免将完整来源数据写入测试日志。</p>
     */
    @Test
    void reportsTraditionalConflictDimensionsWithoutPersistingSourceRecords() {
        Long organizationId = batchMapper.findEnabledOrganizationId(ORGANIZATION_CODE);
        assertNotNull(organizationId);
        Map<String, List<Icd10Entry>> entriesByCode = new LinkedHashMap<>();
        for (Icd10Entry entry : readAllEntries(organizationId, Icd10DiagnosisCategory.TRADITIONAL)) {
            if (invalidReason(entry) == null) {
                entriesByCode.computeIfAbsent(entry.diseaseCode().trim(), ignored -> new ArrayList<>()).add(entry);
            }
        }
        List<String> samples = entriesByCode.entrySet().stream()
                .filter(entry -> entry.getValue().stream().distinct().count() > 1)
                .limit(10)
                .map(entry -> conflictDimensions(entry.getKey(), entry.getValue()))
                .toList();
        List<List<Icd10Entry>> conflicts = entriesByCode.values().stream()
                .filter(entries -> entries.stream().distinct().count() > 1)
                .toList();
        System.out.println("100-006 中医诊断同编码差异维度样本=" + samples);
        System.out.println("100-006 中医诊断冲突统计：组数=" + conflicts.size()
                + "，名称差异=" + countDifferences(conflicts, Icd10Entry::diseaseName)
                + "，助记码差异=" + countDifferences(conflicts, Icd10Entry::mnemonicCode)
                + "，备注差异=" + countDifferences(conflicts, Icd10Entry::remark)
                + "，创建时间差异=" + countDifferences(conflicts, Icd10Entry::sourceCreatedAt)
                + "，疾病ID差异=" + countDifferences(conflicts, Icd10Entry::sourceDiseaseId));
    }

    /**
     * 只读统计原始疾病ID与疾病编码的基数关系，确认当前来源响应可使用的自然键。
     *
     * <p>不输出疾病名称、备注、时间或来源ID正文；统计仅用于决定平台去重和幂等键，
     * 不创建批次或写入目录。</p>
     */
    @Test
    void reportsIcd10SourceIdentityStructureWithoutPersistingSourceRecords() {
        Long organizationId = batchMapper.findEnabledOrganizationId(ORGANIZATION_CODE);
        assertNotNull(organizationId);
        for (Icd10DiagnosisCategory category : Icd10DiagnosisCategory.values()) {
            List<Icd10Entry> entries = readAllEntries(organizationId, category);
            Map<String, Set<String>> codesBySourceId = new LinkedHashMap<>();
            Map<String, Long> occurrencesBySourceId = new LinkedHashMap<>();
            Map<String, Long> occurrencesByCode = new LinkedHashMap<>();
            long missingSourceId = 0;
            int maximumMnemonicLength = 0;
            for (Icd10Entry entry : entries) {
                String diseaseCode = entry.diseaseCode().trim();
                occurrencesByCode.merge(diseaseCode, 1L, Long::sum);
                maximumMnemonicLength = Math.max(maximumMnemonicLength, trimmedLength(entry.mnemonicCode()));
                if (isBlank(entry.sourceDiseaseId())) {
                    missingSourceId++;
                    continue;
                }
                String sourceId = entry.sourceDiseaseId().trim();
                occurrencesBySourceId.merge(sourceId, 1L, Long::sum);
                codesBySourceId.computeIfAbsent(sourceId, ignored -> new LinkedHashSet<>()).add(diseaseCode);
            }
            long repeatedSourceId = occurrencesBySourceId.values().stream().filter(count -> count > 1).count();
            long sourceIdMappedToMultipleCodes = codesBySourceId.values().stream().filter(codes -> codes.size() > 1).count();
            long repeatedDiseaseCode = occurrencesByCode.values().stream().filter(count -> count > 1).count();
            System.out.println("100-006 " + category.displayName() + "原始键统计：总行=" + entries.size()
                    + "，空疾病ID=" + missingSourceId + "，不同疾病ID=" + occurrencesBySourceId.size()
                    + "，重复疾病ID=" + repeatedSourceId + "，疾病ID映射多编码=" + sourceIdMappedToMultipleCodes
                    + "，重复疾病编码=" + repeatedDiseaseCode + "，最大助记码长度=" + maximumMnemonicLength);
        }
    }

    /** 读取一个类别的所有100-006分页记录，只用于联调诊断。 */
    private List<Icd10Entry> readAllEntries(long organizationId, Icd10DiagnosisCategory category) {
        PhisResponse<Long> countResponse = phisService.countIcd10(organizationId, ParameterEnvironment.TEST,
                new Icd10CountQuery(null, RANGE_START, RANGE_END, category, null));
        assertTrue(countResponse.success(), category.displayName() + "诊断目录行数查询失败：" + countResponse.errorMessage());
        assertNotNull(countResponse.data());
        List<Icd10Entry> entries = new ArrayList<>();
        for (long startRow = 1; startRow <= countResponse.data(); startRow += 100) {
            long endRow = Math.min(countResponse.data() + 1, startRow + 100);
            PhisResponse<List<Icd10Entry>> page = phisService.queryIcd10(organizationId, ParameterEnvironment.TEST,
                    new Icd10Query(null, startRow, endRow, RANGE_START, RANGE_END, category, null));
            assertTrue(page.success(), category.displayName() + "诊断目录分页查询失败：" + page.errorMessage());
            assertNotNull(page.data());
            entries.addAll(page.data());
        }
        return entries;
    }

    /** 返回与发布校验一致的无效原因；返回空代表字段满足最小发布约束。 */
    private String invalidReason(Icd10Entry entry) {
        if (entry == null) return "记录为空";
        if (isBlank(entry.diseaseCode())) return "疾病编码为空";
        if (isBlank(entry.diseaseName())) return "病种名称为空";
        if (isBlank(entry.sourceCreatedAt())) return "创建时间为空";
        if (entry.diseaseCode().trim().length() > 50) return "疾病编码超过50字符";
        if (entry.diseaseName().trim().length() > 50) return "病种名称超过50字符";
        if (trimmedLength(entry.mnemonicCode()) > 64) return "助记码超过64字符";
        if (trimmedLength(entry.remark()) > 100) return "备注超过100字符";
        if (entry.sourceCreatedAt().trim().length() > 50) return "创建时间超过50字符";
        if (trimmedLength(entry.sourceDiseaseId()) > 32) return "疾病ID超过32字符";
        return null;
    }

    /** 构造不暴露完整名称和备注正文的诊断样本。 */
    private String sample(Icd10Entry entry) {
        return "编码=" + entry.diseaseCode() + "，名称长度=" + trimmedLength(entry.diseaseName())
                + "，助记码长度=" + trimmedLength(entry.mnemonicCode())
                + "，备注长度=" + trimmedLength(entry.remark())
                + "，创建时间长度=" + trimmedLength(entry.sourceCreatedAt())
                + "，疾病ID长度=" + trimmedLength(entry.sourceDiseaseId());
    }

    /** 返回同一疾病编码多条记录是否在各目录或追溯字段发生差异。 */
    private String conflictDimensions(String diseaseCode, List<Icd10Entry> entries) {
        return "编码=" + diseaseCode + "，名称差异=" + hasDifference(entries, Icd10Entry::diseaseName)
                + "，助记码差异=" + hasDifference(entries, Icd10Entry::mnemonicCode)
                + "，备注差异=" + hasDifference(entries, Icd10Entry::remark)
                + "，创建时间差异=" + hasDifference(entries, Icd10Entry::sourceCreatedAt)
                + "，疾病ID差异=" + hasDifference(entries, Icd10Entry::sourceDiseaseId);
    }

    /** 判断同编码多条记录的指定字段是否存在多个规范化值。 */
    private boolean hasDifference(List<Icd10Entry> entries, Function<Icd10Entry, String> extractor) {
        return entries.stream().map(extractor).map(value -> value == null ? null : value.trim()).distinct().count() > 1;
    }

    /** 统计同编码冲突组中指定字段存在差异的组数。 */
    private long countDifferences(List<List<Icd10Entry>> conflicts, Function<Icd10Entry, String> extractor) {
        return conflicts.stream().filter(entries -> hasDifference(entries, extractor)).count();
    }

    /** 判断文本在去除首尾空白后是否为空。 */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 返回去除首尾空白后的长度；空值按零处理。 */
    private int trimmedLength(String value) {
        return value == null ? 0 : value.trim().length();
    }
}
