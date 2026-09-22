package cn.zqkj.platform.masterdata.service.hospitaldirectory;

import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;

/** 将已取得执行权的医院综合目录批次同步为当前数据。 */
public interface HospitalDirectorySyncService {

    /**
     * 同步批次内的各目录类型，将类型数据与结果同事务保存，最后汇总并审计。
     *
     * <p>调用方须先完成范围确认和批次抢占；失败或结果未知的类型保留原数据。</p>
     *
     * @param batch 已由批次用例确认来源并取得执行权的快照
     * @param actorUserId 审计用户主键；系统任务可为空
     * @param actorLogin 审计操作人名称或系统任务名称
     */
    void synchronize(MasterDataBatchSnapshot batch, Long actorUserId, String actorLogin);
    /**
     * 核对执行版本后按已保存事实结束批次；不重新取数，未保存类型保持结果未知。
     * @param batch 当前执行版本快照
     * @param actorUserId 审计用户主键；内部任务可为空
     * @param actorLogin 操作人或任务名称
     */
    void completeRecordedResults(MasterDataBatchSnapshot batch, Long actorUserId, String actorLogin);
}
