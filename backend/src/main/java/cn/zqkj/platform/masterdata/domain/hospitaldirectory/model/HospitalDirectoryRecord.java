package cn.zqkj.platform.masterdata.domain.hospitaldirectory.model;

import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryType;

import java.time.LocalDateTime;

/**
 * 平台当前有效的一条医院综合目录查询投影。
 *
 * <p>数据来源：主表 {@code dbo.md_hospital_directory}（医院综合目录正式数据表），
 * 关联 {@code dbo.org_organization}、{@code dbo.md_sync_batch}，并统计
 * {@code dbo.md_hospital_directory_relation}中的当前有效关系。</p>
 *
 * <p>业务说明：只表示已完整取得、通过自动校验并完成对账的当前有效目录。</p>
 *
 * @param id 平台目录主键
 * @param organizationCode 平台机构编码
 * @param organizationName 平台机构名称
 * @param directoryType 目录类型
 * @param sourceRecordCode HIS目录编码
 * @param sourceRecordName HIS目录名称
 * @param mnemonicCode 可选助记码
 * @param categoryName 可选来源类别
 * @param remark 可选非敏感备注
 * @param sourceOrganizationCode 来源返回的可选机构编码
 * @param relationCount 当前有效关系数量
 * @param latestBatchId 最近成功批次主键
 * @param latestBatchNo 最近成功批次编号
 * @param lastSeenAt 最近成功同步时间
 */
public record HospitalDirectoryRecord(
        long id,
        String organizationCode,
        String organizationName,
        HospitalDirectoryType directoryType,
        String sourceRecordCode,
        String sourceRecordName,
        String mnemonicCode,
        String categoryName,
        String remark,
        String sourceOrganizationCode,
        long relationCount,
        long latestBatchId,
        String latestBatchNo,
        LocalDateTime lastSeenAt
) {
}
