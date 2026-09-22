package cn.zqkj.platform.masterdata.domain.medicaldirectory.model;

import cn.zqkj.platform.common.utils.Func;

/**
 * 用于医疗目录对账的标准化来源事实，空白文本统一为无值。
 *
 * <p>字段由HIS适配层转换而来，仅保留基础数据对账所需内容；本类型不是HIS报文模型。</p>
 *
 * @param directoryCode 来源稳定目录编码
 * @param directoryName 来源目录名称
 * @param mnemonicCode 可选助记码
 * @param categoryName 来源类别名称
 * @param unit 可选单位
 * @param specification 可选规格
 * @param dosageForm 可选剂型
 * @param manufacturerName 可选生产厂家
 * @param remark 可选备注
 * @param sourceCreatedAt 来源创建时间原文
 * @param packageUnit 可选包装单位
 * @param conversionFactor 可选转换系数原文
 * @param approvalNumber 可选批准文号
 * @param standardCode 可选标准编码
 * @param packageMaterial 可选包装材质
 * @param processingMethod 可选炮制方法
 * @param region 可选地区
 * @param category 可选来源类别
 * @param enabledFlag 来源启用原始值
 */
public record MedicalDirectorySourceEntry(
        String directoryCode,
        String directoryName,
        String mnemonicCode,
        String categoryName,
        String unit,
        String specification,
        String dosageForm,
        String manufacturerName,
        String remark,
        String sourceCreatedAt,
        String packageUnit,
        String conversionFactor,
        String approvalNumber,
        String standardCode,
        String packageMaterial,
        String processingMethod,
        String region,
        String category,
        String enabledFlag
) {
    /**
     * 在来源事实进入平台时统一文本，避免对账过程重复构造同一记录。
     *
     * @param directoryCode 来源稳定目录编码
     * @param directoryName 来源目录名称
     * @param mnemonicCode 可选助记码
     * @param categoryName 来源类别名称
     * @param unit 可选单位
     * @param specification 可选规格
     * @param dosageForm 可选剂型
     * @param manufacturerName 可选生产厂家
     * @param remark 可选备注
     * @param sourceCreatedAt 来源创建时间原文
     * @param packageUnit 可选包装单位
     * @param conversionFactor 可选转换系数原文
     * @param approvalNumber 可选批准文号
     * @param standardCode 可选标准编码
     * @param packageMaterial 可选包装材质
     * @param processingMethod 可选炮制方法
     * @param region 可选地区
     * @param category 可选来源类别
     * @param enabledFlag 来源启用原始值
     */
    public MedicalDirectorySourceEntry {
        directoryCode = Func.trimToNull(directoryCode);
        directoryName = Func.trimToNull(directoryName);
        mnemonicCode = Func.trimToNull(mnemonicCode);
        categoryName = Func.trimToNull(categoryName);
        unit = Func.trimToNull(unit);
        specification = Func.trimToNull(specification);
        dosageForm = Func.trimToNull(dosageForm);
        manufacturerName = Func.trimToNull(manufacturerName);
        remark = Func.trimToNull(remark);
        sourceCreatedAt = Func.trimToNull(sourceCreatedAt);
        packageUnit = Func.trimToNull(packageUnit);
        conversionFactor = Func.trimToNull(conversionFactor);
        approvalNumber = Func.trimToNull(approvalNumber);
        standardCode = Func.trimToNull(standardCode);
        packageMaterial = Func.trimToNull(packageMaterial);
        processingMethod = Func.trimToNull(processingMethod);
        region = Func.trimToNull(region);
        category = Func.trimToNull(category);
        enabledFlag = Func.trimToNull(enabledFlag);
    }
}
