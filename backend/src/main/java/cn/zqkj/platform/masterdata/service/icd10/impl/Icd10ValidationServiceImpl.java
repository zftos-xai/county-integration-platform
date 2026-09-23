package cn.zqkj.platform.masterdata.service.icd10.impl;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10SourceRecord;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10ValidationResult;
import cn.zqkj.platform.masterdata.service.icd10.Icd10ValidationService;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 实现公共ICD10目录的声明数量、必填字段、重复和冲突校验。
 *
 * <p>本服务按来源疾病ID识别单次响应内的重复或冲突；来源未提供该ID时才保守回退到疾病编码。</p>
 */
@Service
public class Icd10ValidationServiceImpl implements Icd10ValidationService {

    /**
     * 核对单一类别的来源声明数量、字段完整性及冲突事实。
     *
     * <p>类别加来源稳定身份用于识别本次响应内的重复或冲突；发现同一来源实体出现差异时，
     * 不返回可被误写入的部分数据。相同疾病编码但疾病ID不同的记录是独立来源实体，必须保留。</p>
     */
    @Override
    public Icd10ValidationResult validate(long declaredCount, List<Icd10SourceRecord> records) {
        if (declaredCount < 0) {
            throw new IllegalArgumentException("来源声明行数不能为负数");
        }
        List<Icd10SourceRecord> source = records == null ? List.of() : records;
        Map<String, Icd10SourceRecord> accepted = new LinkedHashMap<>();
        Set<String> conflictedKeys = new HashSet<>();
        long invalid = 0;
        long duplicate = 0;
        long conflict = 0;
        for (Icd10SourceRecord record : source) {
            if (!isValid(record)) {
                invalid++;
                continue;
            }
            String responseKey = record.category().code() + "|" + record.sourceRecordKey();
            if (conflictedKeys.contains(responseKey)) {
                duplicate++;
                continue;
            }
            Icd10SourceRecord previous = accepted.putIfAbsent(responseKey, record);
            if (previous == null) {
                continue;
            }
            if (previous.entry().equals(record.entry())) {
                duplicate++;
            } else {
                conflict++;
                accepted.remove(responseKey);
                conflictedKeys.add(responseKey);
            }
        }
        String failure = declaredCount == source.size() && duplicate == 0 && invalid == 0 && conflict == 0 ? null
                : "来源声明" + declaredCount + "条，实际取得" + source.size() + "条；无效" + invalid
                        + "条，重复" + duplicate + "条，冲突" + conflict + "组";
        return new Icd10ValidationResult(declaredCount, source.size(), duplicate, invalid, conflict,
                List.copyOf(accepted.values()), failure);
    }

    /** 判断单条来源诊断记录是否满足接口文档明确的最小发布字段与长度。 */
    private boolean isValid(Icd10SourceRecord record) {
        if (record == null || record.category() == null || record.entry() == null) {
            return false;
        }
        var entry = record.entry();
        return Func.isNotBlank(entry.diseaseCode()) && Func.isNotBlank(entry.diseaseName())
                && Func.isNotBlank(entry.sourceCreatedAt())
                && !Func.exceedsTrimmedLength(entry.diseaseCode(), 50)
                && !Func.exceedsTrimmedLength(entry.diseaseName(), 64)
                && !Func.exceedsTrimmedLength(entry.mnemonicCode(), 64)
                && !Func.exceedsTrimmedLength(entry.remark(), 100)
                && !Func.exceedsTrimmedLength(entry.sourceCreatedAt(), 50)
                && !Func.exceedsTrimmedLength(entry.sourceDiseaseId(), 32);
    }
}
