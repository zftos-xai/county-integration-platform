package cn.zqkj.platform.masterdata.service.medicaldirectory;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryValidationResult;

import java.util.List;

/** 对100-004全部分页结果执行不依赖人工判断的同步输入校验。 */
public interface MedicalDirectoryValidationService {

    /**
     * 校验100-005声明数量与100-004全部分页结果的一致性和可写性。
     *
     * @param declaredCount 同范围100-005声明行数
     * @param records 全部成功分页取得的原始记录
     * @return 确定性校验结果
     */
    MedicalDirectoryValidationResult validate(long declaredCount, List<MedicalDirectorySourceRecord> records);
}
