package cn.zqkj.platform.masterdata.service.hospitaldirectory;

import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;

/**
 * 执行100-003医院综合目录的一次性取得、自动校验和直接对账。
 */
public interface HospitalDirectorySyncService {

    /**
     * 一次取得科室、医生、病区和床位；每一种目录完整返回并通过自动校验后即更新当前目录。
     * 失败或结果未知的类型不会更新、更不会将旧记录标为无效。
     *
     * @param batchId 批次主键
     * @param expectedVersion 最近读取的批次版本
     * @param actor 发起人
     * @return 已完成或部分异常的最新同步运行事实
     */
    MasterDataBatchSummaryVO fetchValidateAndReconcile(long batchId, byte[] expectedVersion, AccessActor actor);
}
