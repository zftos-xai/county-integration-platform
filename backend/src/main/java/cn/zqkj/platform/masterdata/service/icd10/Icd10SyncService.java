package cn.zqkj.platform.masterdata.service.icd10;

import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;

/** 将已取得执行权的公共ICD10批次同步为平台当前目录。 */
public interface Icd10SyncService {

    /**
     * 分别同步西医和中医诊断类别，并保存独立运行事实。
     *
     * @param batch 已由批次服务确认的公共批次
     * @param endpointOrganizationId 仅用于取得冻结端点凭证的机构主键
     * @param actorUserId 审计用户主键
     * @param actorLogin 审计操作人
     */
    void synchronize(MasterDataBatchSnapshot batch, long endpointOrganizationId, Long actorUserId, String actorLogin);

    /**
     * 仅基于已保存分项结果结束中断批次，不重新调用HIS。
     *
     * @param batch 当前执行版本快照
     * @param actorUserId 审计用户主键
     * @param actorLogin 审计操作人
     */
    void completeRecordedResults(MasterDataBatchSnapshot batch, Long actorUserId, String actorLogin);
}
