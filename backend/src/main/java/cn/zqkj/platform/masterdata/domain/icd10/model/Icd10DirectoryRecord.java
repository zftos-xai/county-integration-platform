package cn.zqkj.platform.masterdata.domain.icd10.model;

import java.time.LocalDateTime;

/**
 * 平台公共ICD10目录的一条只读查询投影。
 *
 * <p>数据来源：{@code dbo.md_icd10_diagnosis}，关联{@code dbo.md_sync_batch}补充最近批次。业务说明：
 * 不携带机构归属或端点所属机构。</p>
 *
 * @param id 公共目录主键
 * @param diagnosisCategory 西医或中医诊断类别
 * @param diseaseCode HIS疾病编码
 * @param diseaseName HIS病种名称
 * @param mnemonicCode 可选助记码
 * @param remark 可选备注
 * @param sourceCreatedAt HIS创建时间原文
 * @param sourceDiseaseId 可选来源疾病标识
 * @param latestBatchId 最近同步批次主键
 * @param latestBatchNo 最近同步批次号
 * @param lastSeenAt 最近成功观察UTC时间
 */
public record Icd10DirectoryRecord(long id, Icd10DiagnosisCategory diagnosisCategory, String diseaseCode,
                                   String diseaseName, String mnemonicCode, String remark, String sourceCreatedAt,
                                   String sourceDiseaseId, long latestBatchId, String latestBatchNo,
                                   LocalDateTime lastSeenAt) { }
