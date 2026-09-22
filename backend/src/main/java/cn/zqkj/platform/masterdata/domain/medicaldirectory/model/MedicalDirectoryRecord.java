package cn.zqkj.platform.masterdata.domain.medicaldirectory.model;

import java.time.LocalDateTime;

/**
 * 平台当前有效的一条医疗目录查询投影。
 *
 * <p>数据来源：{@code dbo.md_medical_directory}（医疗目录当前数据表），并关联
 * {@code dbo.org_organization}和{@code dbo.md_sync_batch}组装机构和来源批次链路。
 * 业务说明：该投影只用于只读展示，不代表新的暂存或人工维护对象。</p>
 *
 * @param id 平台目录主键
 * @param organizationCode 平台机构编码
 * @param organizationName 平台机构名称
 * @param directoryType 医疗目录类型
 * @param sourceRecordCode HIS目录稳定编码
 * @param sourceRecordName HIS目录名称
 * @param mnemonicCode 可选助记码
 * @param categoryName 来源目录类别名称
 * @param unit 可选单位
 * @param specification 可选规格
 * @param manufacturerName 可选生产厂家
 * @param sourceEnabledFlag HIS启用状态原始值
 * @param sourceCreatedAt HIS返回创建时间原文
 * @param dosageForm 来源剂型
 * @param remark 来源备注
 * @param packageUnit 来源包装单位
 * @param conversionFactor 来源转换系数原文
 * @param approvalNumber 来源国药准字号
 * @param standardCode 来源药品本位码
 * @param packageMaterial 来源包装材质
 * @param processingMethod 来源炮制方法
 * @param region 来源地区
 * @param category 来源类别
 * @param latestBatchId 最近成功批次主键
 * @param latestBatchNo 最近成功批次编号
 * @param sourceOrganizationId 最近调用实际使用的HIS来源机构参数
 * @param lastSeenAt 最近成功同步时间
 */
public record MedicalDirectoryRecord(
        long id,
        String organizationCode,
        String organizationName,
        MedicalDirectoryType directoryType,
        String sourceRecordCode,
        String sourceRecordName,
        String mnemonicCode,
        String categoryName,
        String unit,
        String specification,
        String manufacturerName,
        String sourceEnabledFlag,
        String sourceCreatedAt,
        String dosageForm,
        String remark,
        String packageUnit,
        String conversionFactor,
        String approvalNumber,
        String standardCode,
        String packageMaterial,
        String processingMethod,
        String region,
        String category,
        long latestBatchId,
        String latestBatchNo,
        String sourceOrganizationId,
        LocalDateTime lastSeenAt
) {
}
