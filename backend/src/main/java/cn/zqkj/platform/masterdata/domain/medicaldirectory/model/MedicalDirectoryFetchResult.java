package cn.zqkj.platform.masterdata.domain.medicaldirectory.model;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;

/**
 * 保存一类医院目录完成数量查询和全部分页后的自动校验事实。
 *
 * @param directoryType 本次取得的目录类型
 * @param validation 分页结果的自动校验结论
 */
public record MedicalDirectoryFetchResult(
        MedicalDirectoryType directoryType,
        MedicalDirectoryValidationResult validation
) {

    /**
     * 创建一次完整取得结果。
     *
     * @param directoryType 本次取得的目录类型
     * @param validation 分页结果的自动校验结论
     */
    public MedicalDirectoryFetchResult {
        if (directoryType == null || validation == null) {
            throw new IllegalArgumentException("目录类型和校验结果不能为空");
        }
    }
}
