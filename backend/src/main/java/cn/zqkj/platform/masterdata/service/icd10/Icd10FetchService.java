package cn.zqkj.platform.masterdata.service.icd10;

import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10DiagnosisCategory;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10ValidationResult;

/** 取得并校验单个公共ICD10诊断类别的完整来源结果。 */
public interface Icd10FetchService {

    /**
     * 以100-007声明数和100-006分页结果核对一个诊断类别。
     *
     * @param batch 已锁定范围与端点版本的公共批次
     * @param endpointOrganizationId 只用于解析所选端点凭证的机构主键
     * @param category 本次明确请求的诊断类别
     * @return 已校验来源结果
     */
    Icd10ValidationResult fetchAll(
            MasterDataBatchSnapshot batch, long endpointOrganizationId, Icd10DiagnosisCategory category);
}
