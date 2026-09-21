package cn.zqkj.platform.masterdata.service;

import cn.zqkj.platform.masterdata.domain.model.MedicalDirectoryReconciliationResult;
import cn.zqkj.platform.masterdata.domain.model.MedicalDirectoryValidationResult;

import java.util.Set;

/** 根据完整HIS目录范围计算当前记录的新增、更新和缺失失效动作。 */
public interface MedicalDirectoryReconciliationService {

    /**
     * 仅在来源范围完整且全部自动校验通过时，计算平台当前目录中应标记无效的缺失编码。
     *
     * @param validation 本次100-004完整分页后的自动校验结果
     * @param currentCodes 当前平台同机构、同来源、同目录类型的有效稳定编码
     * @return 不含数据库操作的确定性更新动作
     */
    MedicalDirectoryReconciliationResult reconcile(
            MedicalDirectoryValidationResult validation,
            Set<String> currentCodes
    );
}
