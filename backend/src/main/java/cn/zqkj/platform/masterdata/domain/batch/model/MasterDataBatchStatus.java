package cn.zqkj.platform.masterdata.domain.batch.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 定义基础数据同步批次的稳定状态和允许迁移方向。
 *
 * <p>同步运行只记录真实处理结果，不承载人工发布或版本切换语义。</p>
 */
public enum MasterDataBatchStatus {

    /** 批次已创建，尚未开始调用来源系统。 */
    CREATED,
    /** 正在分页取数、校验或执行本地对账。 */
    FETCHING,
    /** 所有请求范围均已取得、校验并对账完成。 */
    COMPLETED,
    /** 部分目录已完成对账，其他目录收到了可确认的失败结果。 */
    COMPLETED_WITH_ERRORS,
    /** 部分目录已完成对账，其他目录因通信或协议异常无法确认结果。 */
    COMPLETED_WITH_UNKNOWN,
    /** 本次运行已明确失败，不得直接当作来源数据缺失。 */
    FAILED,
    /** 平台未能确认来源系统是否完成处理，需先核查再决定是否重试。 */
    RESULT_UNKNOWN;

    private static final Map<MasterDataBatchStatus, Set<MasterDataBatchStatus>> TRANSITIONS = Map.of(
            CREATED, EnumSet.of(FETCHING, FAILED),
            FETCHING, EnumSet.of(COMPLETED, COMPLETED_WITH_ERRORS, COMPLETED_WITH_UNKNOWN, FAILED, RESULT_UNKNOWN),
            RESULT_UNKNOWN, EnumSet.of(FETCHING, FAILED),
            COMPLETED, EnumSet.noneOf(MasterDataBatchStatus.class),
            COMPLETED_WITH_ERRORS, EnumSet.noneOf(MasterDataBatchStatus.class),
            COMPLETED_WITH_UNKNOWN, EnumSet.noneOf(MasterDataBatchStatus.class),
            FAILED, EnumSet.noneOf(MasterDataBatchStatus.class)
    );

    /**
     * 判断当前状态是否允许迁移到目标状态。
     *
     * @param target 目标状态
     * @return 允许迁移时为true；相同状态也返回false
     */
    public boolean canTransitionTo(MasterDataBatchStatus target) {
        return target != null && TRANSITIONS.get(this).contains(target);
    }

    /**
     * 判断批次是否仍占用同一业务范围的运行名额。
     *
     * @return 仍可能改变处理结果时为true
     */
    public boolean isActive() {
        return this == CREATED || this == FETCHING || this == RESULT_UNKNOWN;
    }

    /**
     * 判断同步运行是否已完成，且所有已请求目录类型均已自动对账。
     *
     * @return 所有目录类型均成功完成时为true
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }
}
