package cn.zqkj.platform.masterdata.service.medicaldirectory.impl;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryReconciliationResult;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryValidationResult;
import cn.zqkj.platform.masterdata.service.medicaldirectory.MedicalDirectoryReconciliationService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/** 实现完整来源范围才允许标记HIS缺失记录无效的对账规则。 */
@Service
public class MedicalDirectoryReconciliationServiceImpl implements MedicalDirectoryReconciliationService {

    /**
     * {@inheritDoc}
     *
     * <p>仅比较已经完整校验的数据集，分别给出新增、更新、失效和未变化数量，不在此阶段写库。</p>
     */
    @Override
    public MedicalDirectoryReconciliationResult reconcile(
            MedicalDirectoryValidationResult validation,
            Set<String> currentCodes
    ) {
        if (validation == null) {
            throw new IllegalArgumentException("必须提供目录自动校验结果");
        }
        if (!validation.valid()) {
            return new MedicalDirectoryReconciliationResult(validation.acceptedRecords(), Set.of());
        }
        Set<String> sourceCodes = new LinkedHashSet<>();
        validation.acceptedRecords().forEach(record -> sourceCodes.add(record.entry().directoryCode().trim()));
        Set<String> invalid = new LinkedHashSet<>();
        if (currentCodes != null) {
            currentCodes.stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(String::trim)
                    .filter(code -> !sourceCodes.contains(code))
                    .forEach(invalid::add);
        }
        return new MedicalDirectoryReconciliationResult(validation.acceptedRecords(), invalid);
    }
}
