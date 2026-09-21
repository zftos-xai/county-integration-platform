package cn.zqkj.platform.his.domain.medicaldirectory.model;

/**
 * 表示100-004返回的一条药品、诊疗或耗材目录记录。
 *
 * <p>字段名称严格来自接口文档；可选扩展字段仅在目标HIS实际返回时保存，不据此推断停用语义。</p>
 *
 * @param directoryCode 来源稳定目录编码
 * @param directoryName 目录名称
 * @param mnemonicCode 助记码
 * @param categoryName 目录类别名称
 * @param unit 单位
 * @param specification 规格
 * @param dosageForm 剂型
 * @param manufacturerName 生产厂家名称
 * @param remark 备注
 * @param sourceCreatedAt 来源创建时间文本
 * @param packageUnit 包装单位
 * @param conversionFactor 转换系数文本
 * @param approvalNumber 国药准字号
 * @param standardCode 药品本位码
 * @param packageMaterial 包装材质
 * @param processingMethod 炮制方法
 * @param region 地区
 * @param category 类别
 * @param enabledFlag 来源是否启用原始值
 */
public record MedicalDirectoryEntry(
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
}
