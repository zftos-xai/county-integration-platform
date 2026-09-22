package cn.zqkj.platform.masterdata.domain.batch.model;

import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import java.time.LocalDateTime;

/**
 * 保存同步批次的来源、模式及服务端确定的查询范围。
 *
 * <p>写入目标：{@code dbo.md_sync_batch}（基础数据同步批次表）。业务说明：将入口选择与实际查询范围一起固化；这不是完整表行。</p>
 *
 * @param requestKey 一次用户操作的稳定标识
 * @param organizationCode 平台机构代码
 * @param environment 来源环境
 * @param category 同步业务
 * @param mode 同步模式
 * @param rangeStart 已确定的来源查询起点UTC时间
 * @param rangeEnd 已确定的来源查询终点UTC时间
 * @param sourceEndpointId 创建时选中的端点主键
 * @param sourceEndpointVersion 创建时选中的端点行版本；运行前检查配置未变
 * @param fullRuleEvidence 全量模式的查询范围说明；非全量为空，字段名沿用既有表结构
 */
public record MasterDataBatchCreation(
        String requestKey,
        String organizationCode,
        ParameterEnvironment environment,
        MasterDataCategory category,
        MasterDataSyncMode mode,
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd,
        long sourceEndpointId,
        byte[] sourceEndpointVersion,
        String fullRuleEvidence
) {
}
