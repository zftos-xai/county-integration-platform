package cn.zqkj.platform.masterdata.domain.icd10.vo;

import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10DiagnosisCategory;

/**
 * 一个公共ICD10诊断类别当前已发布记录数量。
 *
 * <p>数据来源：{@code dbo.md_icd10_diagnosis}。业务说明：仅用于公共目录类别导航数量展示。</p>
 * @param diagnosisCategory 西医或中医类别
 * @param total 当前公共目录数量
 */
public record Icd10DirectoryCountVO(Icd10DiagnosisCategory diagnosisCategory, long total) { }
