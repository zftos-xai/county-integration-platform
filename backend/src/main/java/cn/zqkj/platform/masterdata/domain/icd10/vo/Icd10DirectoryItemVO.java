package cn.zqkj.platform.masterdata.domain.icd10.vo;

import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10DiagnosisCategory;
import java.time.OffsetDateTime;

/**
 * 公共ICD10目录一条只读API记录。
 * @param id 公共目录主键
 * @param diagnosisCategory 诊断类别
 * @param diseaseCode 疾病编码
 * @param diseaseName 病种名称
 * @param mnemonicCode 可选助记码
 * @param remark 可选备注
 * @param sourceCreatedAt 来源创建时间原文
 * @param sourceDiseaseId 可选来源疾病标识
 * @param latestBatchId 最近批次主键
 * @param latestBatchNo 最近批次号
 * @param lastSeenAt 最近观察时间
 */
public record Icd10DirectoryItemVO(long id, Icd10DiagnosisCategory diagnosisCategory, String diseaseCode,
                                   String diseaseName, String mnemonicCode, String remark, String sourceCreatedAt,
                                   String sourceDiseaseId, long latestBatchId, String latestBatchNo,
                                   OffsetDateTime lastSeenAt) { }
