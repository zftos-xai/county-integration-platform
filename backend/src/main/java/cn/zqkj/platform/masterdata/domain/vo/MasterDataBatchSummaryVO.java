package cn.zqkj.platform.masterdata.domain.vo;

import cn.zqkj.platform.masterdata.domain.model.MasterDataBatchCounts;
import cn.zqkj.platform.masterdata.domain.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.model.MasterDataScopeType;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;

import java.time.OffsetDateTime;

/**
 * 向正式管理页面返回一条不含凭证和来源正文的同步批次摘要。
 *
 * @param id 平台批次主键
 * @param batchNo 面向业务追踪的批次号
 * @param scopeType 业务归属范围
 * @param organizationCode 平台机构代码；公共目录为空
 * @param organizationName 平台机构名称；公共目录为空
 * @param environment 来源接口运行环境
 * @param category 基础数据类别
 * @param dataTradeCode 数据查询交易码
 * @param countTradeCode 数量查询交易码；来源不提供时为空
 * @param sourceType 来源接口目录类型；接口不需要时为空
 * @param sourceOrganizationId 发起批次时固化的来源机构标识；公共目录为空
 * @param rangeStart 来源查询范围起点
 * @param rangeEnd 来源查询范围终点
 * @param status 批次状态
 * @param counts 批次数量事实
 * @param startedAt 实际开始处理时间
 * @param finishedAt 处理结束时间；活动批次为空
 * @param completedAt 同步完成的时间；未完成时为空
 * @param failureCode 失败或受控终止原因代码；正常批次为空
 * @param failureSummary 面向运维人员的失败摘要；无失败时为空
 * @param version 数据库并发版本编码
 */
public record MasterDataBatchSummaryVO(
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
        OffsetDateTime rangeStart,
        OffsetDateTime rangeEnd,
        MasterDataBatchStatus status,
        MasterDataBatchCounts counts,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime completedAt,
        String failureCode,
        String failureSummary,
        String version
) {
}
