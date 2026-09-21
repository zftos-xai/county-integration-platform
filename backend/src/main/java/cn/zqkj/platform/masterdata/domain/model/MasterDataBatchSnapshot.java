package cn.zqkj.platform.masterdata.domain.model;

import java.time.LocalDateTime;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;

/**
 * 基础数据同步批次的只读事实快照。
 *
 * <p>数据来源：主表 {@code dbo.md_sync_batch}（基础数据同步批次表），可选关联
 * {@code dbo.org_organization}（机构表）补充机构代码和名称。</p>
 *
 * <p>业务说明：固化同步范围、来源交易、状态、数量、失败原因和行版本，用于结果回读和并发控制。</p>
 *
 * @param id 批次主键
 * @param batchNo 批次号
 * @param scopeType 业务归属范围
 * @param organizationCode 平台机构代码
 * @param organizationName 平台机构名称
 * @param environment 来源接口运行环境
 * @param category 数据类别
 * @param dataTradeCode 数据交易码
 * @param countTradeCode 数量交易码
 * @param sourceType 来源目录类型
 * @param sourceOrganizationId 发起批次时固化的已验证来源机构标识
 * @param rangeStart 查询范围开始UTC时间
 * @param rangeEnd 查询范围结束UTC时间
 * @param status 批次状态
 * @param declaredCount 来源声明数
 * @param returnedCount 实际取得数
 * @param duplicateCount 重复数
 * @param invalidCount 无效数
 * @param conflictCount 冲突数
 * @param createdCount 新增数
 * @param updatedCount 更新数
 * @param unchangedCount 未变化数
 * @param sourceMissingCount 来源缺失数
 * @param activeCount 同步完成后有效数
 * @param startedAt 处理开始UTC时间
 * @param finishedAt 处理结束UTC时间
 * @param completedAt 同步完成UTC时间
 * @param failureCode 失败或受控终止原因代码
 * @param failureSummary 失败摘要
 * @param version SQL Server并发版本
 */
public record MasterDataBatchSnapshot(
        long id,
        String batchNo,
        MasterDataScopeType scopeType,
        String organizationCode,
        String organizationName,
        ParameterEnvironment environment,
        MasterDataCategory category,
        String dataTradeCode,
        String countTradeCode,
        String sourceType,
        String sourceOrganizationId,
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd,
        MasterDataBatchStatus status,
        Long declaredCount,
        long returnedCount,
        long duplicateCount,
        long invalidCount,
        long conflictCount,
        long createdCount,
        long updatedCount,
        long unchangedCount,
        long sourceMissingCount,
        Long activeCount,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime completedAt,
        String failureCode,
        String failureSummary,
        byte[] version
) {

    /**
     * 将数据库平铺数量组装为领域数量事实。
     *
     * @return 批次数量事实
     */
    public MasterDataBatchCounts counts() {
        return new MasterDataBatchCounts(
                declaredCount,
                returnedCount,
                duplicateCount,
                invalidCount,
                conflictCount,
                createdCount,
                updatedCount,
                unchangedCount,
                sourceMissingCount,
                activeCount
        );
    }
}
