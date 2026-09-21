package cn.zqkj.platform.masterdata.service.medicaldirectory;

import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;

/**
 * 定义100-004/100-005医疗目录的数量核对、分页取得和当前数据直接同步用例。
 */
public interface MedicalDirectorySyncService {

    /**
     * 在同一批次范围中执行四类医疗目录的数量核对、完整分页取得和当前数据更新。
     *
     * @param batchId 同步批次主键
     * @param expectedVersion 调用方最近读取的批次并发版本
     * @param actor 发起同步的当前用户
     * @return 运行完成后的最新批次摘要
     */
    MasterDataBatchSummaryVO fetchValidateAndReconcile(long batchId, byte[] expectedVersion, AccessActor actor);
}
