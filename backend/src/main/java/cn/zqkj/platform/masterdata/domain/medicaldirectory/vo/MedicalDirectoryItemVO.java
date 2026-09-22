package cn.zqkj.platform.masterdata.domain.medicaldirectory.vo;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;
import java.time.OffsetDateTime;

/**
 * 医疗目录当前有效记录的API输出。
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
 * @param latestBatchId 最近成功批次主键
 * @param latestBatchNo 最近成功批次编号
 * @param sourceOrganizationId 最近调用实际使用的HIS来源机构参数
 * @param lastSeenAt 最近成功同步时间
 */
public record MedicalDirectoryItemVO(
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
        long latestBatchId,
        String latestBatchNo,
        String sourceOrganizationId,
        OffsetDateTime lastSeenAt
) {
}
