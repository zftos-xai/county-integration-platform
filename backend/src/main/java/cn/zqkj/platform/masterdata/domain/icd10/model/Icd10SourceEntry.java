package cn.zqkj.platform.masterdata.domain.icd10.model;

import cn.zqkj.platform.common.utils.Func;

/**
 * 用于公共ICD10目录校验的标准化来源事实。
 *
 * <p>字段由HIS协议适配层转换而来，仅保留诊断目录同步所需内容。来源疾病ID可以为空；
 * 当实际响应提供该值时，由 {@link Icd10SourceRecord} 将其作为跨同步来源身份。</p>
 *
 * @param diseaseCode 来源必填疾病编码
 * @param diseaseName 来源必填病种名称
 * @param mnemonicCode 来源可选助记码
 * @param remark 来源可选备注
 * @param sourceCreatedAt 来源必填创建时间原文，不推断时区
 * @param sourceDiseaseId 来源可选疾病ID
 */
public record Icd10SourceEntry(
        String diseaseCode,
        String diseaseName,
        String mnemonicCode,
        String remark,
        String sourceCreatedAt,
        String sourceDiseaseId
) {

    /**
     * 在来源事实进入校验层时统一空白文本，避免后续重复比较出现空白差异。
     *
     * @param diseaseCode 来源必填疾病编码
     * @param diseaseName 来源必填病种名称
     * @param mnemonicCode 来源可选助记码
     * @param remark 来源可选备注
     * @param sourceCreatedAt 来源必填创建时间原文
     * @param sourceDiseaseId 来源可选疾病ID
     */
    public Icd10SourceEntry {
        diseaseCode = Func.trimToNull(diseaseCode);
        diseaseName = Func.trimToNull(diseaseName);
        mnemonicCode = Func.trimToNull(mnemonicCode);
        remark = Func.trimToNull(remark);
        sourceCreatedAt = Func.trimToNull(sourceCreatedAt);
        sourceDiseaseId = Func.trimToNull(sourceDiseaseId);
    }
}
