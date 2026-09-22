package cn.zqkj.platform.masterdata.service.medicaldirectory;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryValidationResult;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;

/** 取得一类100-004医院目录的全部来源页，并在写库前执行自动校验。 */
public interface MedicalDirectoryFetchService {

    /**
     * 先取得100-005同范围声明行数，再按无重叠分页取得100-004，最后对同一批内存记录校验。
     *
     * <p>任一页返回失败或通信结果未知时立即停止，不自动重试，也不输出部分数据。</p>
     *
     * @param batch 已授权并取得执行权的批次，包含固化范围及交换记录关联信息
     * @param directoryType 医院目录类型
     * @return 不可变的完整取得与校验结果
     */
    MedicalDirectoryValidationResult fetchAll(
            MasterDataBatchSnapshot batch, MedicalDirectoryType directoryType
    );
}
