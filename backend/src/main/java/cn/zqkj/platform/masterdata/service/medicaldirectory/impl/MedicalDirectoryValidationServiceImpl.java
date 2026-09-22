package cn.zqkj.platform.masterdata.service.medicaldirectory.impl;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryValidationResult;
import cn.zqkj.platform.masterdata.service.medicaldirectory.MedicalDirectoryValidationService;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 实现医院三大目录的声明数、必填字段、重复和冲突校验。 */
@Service
public class MedicalDirectoryValidationServiceImpl implements MedicalDirectoryValidationService {

    /**
     * 核对医疗目录的声明数量、字段完整性及同编码数据冲突。
     *
     * <p>同时核对HIS声明数量、实际数量和来源主键唯一性，任何一项不一致都阻止后续对账。</p>
     */
    @Override
    public MedicalDirectoryValidationResult validate(long declaredCount, List<MedicalDirectorySourceRecord> records) {
        if (declaredCount < 0) throw new IllegalArgumentException("来源声明行数不能为负数");
        List<MedicalDirectorySourceRecord> source = records == null ? List.of() : records;
        Map<String, MedicalDirectorySourceRecord> accepted = new LinkedHashMap<>();
        Set<String> conflictedKeys = new HashSet<>();
        long invalid = 0;
        long duplicate = 0;
        long conflict = 0;
        for (MedicalDirectorySourceRecord record : source) {
            if (!isValid(record)) {
                invalid++;
                continue;
            }
            String key = record.type().code() + "|" + record.entry().directoryCode();
            if (conflictedKeys.contains(key)) {
                duplicate++;
                continue;
            }
            MedicalDirectorySourceRecord previous = accepted.putIfAbsent(key, record);
            if (previous == null) continue;
            if (previous.entry().equals(record.entry())) {
                duplicate++;
            } else {
                conflict++;
                accepted.remove(key);
                conflictedKeys.add(key);
            }
        }
        String failure = declaredCount == source.size() && duplicate == 0 && invalid == 0 && conflict == 0 ? null
                : "来源声明" + declaredCount + "条，实际取得" + source.size() + "条；无效" + invalid
                        + "条，重复" + duplicate + "条，冲突" + conflict + "组";
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
        return Func.isNotBlank(entry.directoryCode()) && Func.isNotBlank(entry.directoryName())
                && Func.isNotBlank(entry.categoryName()) && Func.isNotBlank(entry.sourceCreatedAt())
                && Func.isNotBlank(entry.enabledFlag())
                && !Func.exceedsTrimmedLength(entry.directoryCode(), 100)
                && !Func.exceedsTrimmedLength(entry.directoryName(), 300)
                && !Func.exceedsTrimmedLength(entry.mnemonicCode(), 100)
                && !Func.exceedsTrimmedLength(entry.categoryName(), 200)
                && !Func.exceedsTrimmedLength(entry.unit(), 100)
                && !Func.exceedsTrimmedLength(entry.specification(), 500)
                && !Func.exceedsTrimmedLength(entry.dosageForm(), 100)
                && !Func.exceedsTrimmedLength(entry.manufacturerName(), 300)
                && !Func.exceedsTrimmedLength(entry.remark(), 500)
                && !Func.exceedsTrimmedLength(entry.sourceCreatedAt(), 100)
                && !Func.exceedsTrimmedLength(entry.packageUnit(), 100)
                && !Func.exceedsTrimmedLength(entry.conversionFactor(), 100)
                && !Func.exceedsTrimmedLength(entry.approvalNumber(), 100)
                && !Func.exceedsTrimmedLength(entry.standardCode(), 100)
                && !Func.exceedsTrimmedLength(entry.packageMaterial(), 100)
                && !Func.exceedsTrimmedLength(entry.processingMethod(), 100)
                && !Func.exceedsTrimmedLength(entry.region(), 100)
                && !Func.exceedsTrimmedLength(entry.category(), 100)
                && !Func.exceedsTrimmedLength(entry.enabledFlag(), 100);
    }


}
