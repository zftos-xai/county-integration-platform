package cn.zqkj.platform.masterdata.service.medicaldirectory.impl;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryValidationResult;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceEntry;
import cn.zqkj.platform.masterdata.service.medicaldirectory.MedicalDirectoryValidationService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 实现医院三大目录的声明数、必填字段、重复和冲突校验。 */
@Service
public class MedicalDirectoryValidationServiceImpl implements MedicalDirectoryValidationService {

    /**
     * {@inheritDoc}
     *
     * <p>同时核对HIS声明数量、实际数量和来源主键唯一性，任何一项不一致都阻止后续对账。</p>
     */
    @Override
    public MedicalDirectoryValidationResult validate(long declaredCount, List<MedicalDirectorySourceRecord> records) {
        if (declaredCount < 0) throw new IllegalArgumentException("来源声明行数不能为负数");
        List<MedicalDirectorySourceRecord> source = records == null ? List.of() : records;
        Map<String, MedicalDirectorySourceRecord> accepted = new LinkedHashMap<>();
        Set<String> conflictedKeys = new HashSet<>();
        List<String> failures = new ArrayList<>();
        long invalid = 0;
        long duplicate = 0;
        long conflict = 0;
        for (MedicalDirectorySourceRecord record : source) {
            if (!isValid(record)) {
                invalid++;
                continue;
            }
            MedicalDirectorySourceRecord normalized = new MedicalDirectorySourceRecord(record.type(), normalize(record.entry()));
            String key = normalized.type().code() + "|" + normalized.entry().directoryCode();
            if (conflictedKeys.contains(key)) {
                duplicate++;
                continue;
            }
            MedicalDirectorySourceRecord previous = accepted.putIfAbsent(key, normalized);
            if (previous == null) continue;
            if (sameFact(previous, record)) {
                duplicate++;
            } else {
                conflict++;
                accepted.remove(key);
                conflictedKeys.add(key);
                failures.add(normalized.type().displayName() + "目录编码" + normalized.entry().directoryCode() + "返回了冲突内容");
            }
        }
        if (declaredCount != source.size()) {
            failures.add("来源声明" + declaredCount + "条，分页实际取得" + source.size() + "条");
        }
        if (invalid > 0) failures.add("存在" + invalid + "条缺少必填字段或启用值不明确的记录");
        String failure = failures.isEmpty() ? null : String.join("；", failures);
        return new MedicalDirectoryValidationResult(declaredCount, source.size(), duplicate, invalid, conflict,
                List.copyOf(accepted.values()), failure);
    }

    /**
     * 判断医疗目录单条记录是否满足必填字段和启用值要求。
     *
     * @param record 来源记录
     * @return 是否满足当前有接口依据的最小发布字段
     */
    private boolean isValid(MedicalDirectorySourceRecord record) {
        if (record == null || record.entry() == null || record.type() == null) return false;
        var entry = record.entry();
        return hasText(entry.directoryCode()) && hasText(entry.directoryName()) && hasText(entry.categoryName())
                && hasText(entry.sourceCreatedAt()) && hasText(entry.enabledFlag())
                && !exceeds(entry.directoryCode(), 100) && !exceeds(entry.directoryName(), 300)
                && !exceeds(entry.mnemonicCode(), 100) && !exceeds(entry.categoryName(), 200)
                && !exceeds(entry.unit(), 100) && !exceeds(entry.specification(), 500)
                && !exceeds(entry.dosageForm(), 100) && !exceeds(entry.manufacturerName(), 300)
                && !exceeds(entry.remark(), 500) && !exceeds(entry.sourceCreatedAt(), 100)
                && !exceeds(entry.packageUnit(), 100) && !exceeds(entry.conversionFactor(), 100)
                && !exceeds(entry.approvalNumber(), 100) && !exceeds(entry.standardCode(), 100)
                && !exceeds(entry.packageMaterial(), 100) && !exceeds(entry.processingMethod(), 100)
                && !exceeds(entry.region(), 100) && !exceeds(entry.category(), 100)
                && !exceeds(entry.enabledFlag(), 100);
    }

    /**
     * 判断同一稳定编码的两条来源记录是否为相同业务事实。
     *
     * @param left 已接收记录
     * @param right 新记录
     * @return 两条记录是否为相同主事实
     */
    private boolean sameFact(MedicalDirectorySourceRecord left, MedicalDirectorySourceRecord right) {
        return left.entry().equals(right.entry());
    }

    /**
     * 判断来源字段是否包含非空白文本。
     *
     * @param value 可选文本
     * @return 是否为非空白文本
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 规范可选来源文本，避免仅因首尾空白导致错误更新或重复。
     *
     * @param entry HIS原始目录记录
     * @return 可安全持久化的同一目录事实
     */
    private MedicalDirectorySourceEntry normalize(MedicalDirectorySourceEntry entry) {
        return new MedicalDirectorySourceEntry(
                trim(entry.directoryCode()), trim(entry.directoryName()), trim(entry.mnemonicCode()),
                trim(entry.categoryName()), trim(entry.unit()), trim(entry.specification()), trim(entry.dosageForm()),
                trim(entry.manufacturerName()), trim(entry.remark()), trim(entry.sourceCreatedAt()),
                trim(entry.packageUnit()), trim(entry.conversionFactor()), trim(entry.approvalNumber()),
                trim(entry.standardCode()), trim(entry.packageMaterial()), trim(entry.processingMethod()),
                trim(entry.region()), trim(entry.category()), trim(entry.enabledFlag()));
    }

    /**
     * 判断文本是否超过已建字段的最大长度。
     *
     * @param value 可选文本
     * @param maximum 数据库字段最大长度
     * @return 超过最大长度时为true
     */
    private boolean exceeds(String value, int maximum) {
        return value != null && value.trim().length() > maximum;
    }

    /**
     * 去掉文本首尾空白，并将空白值统一为无值。
     *
     * @param value 可选来源文本
     * @return 规范文本或空值
     */
    private String trim(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
