package cn.zqkj.platform.his.domain.icd10.model;

/**
 * 表示100-006返回的一条ICD10诊断来源记录。
 *
 * <p>字段严格收敛自接口文档。文档在“疾病编码”和“病种编码”之间存在命名不一致，协议适配层仅在两者一致或仅出现其一时映射为{@code diseaseCode}；来源疾病ID可为空，尚不作为平台唯一键。</p>
 *
 * @param diseaseCode 来源必填疾病或病种编码
 * @param diseaseName 来源必填病种名称
 * @param mnemonicCode 来源可选助记码
 * @param remark 来源可选备注
 * @param sourceCreatedAt 来源必填创建时间原文，不推断来源时区
 * @param sourceDiseaseId 来源可选疾病ID，不推断其跨同步稳定性
 */
public record Icd10Entry(
        String diseaseCode,
        String diseaseName,
        String mnemonicCode,
        String remark,
        String sourceCreatedAt,
        String sourceDiseaseId
) {
}
