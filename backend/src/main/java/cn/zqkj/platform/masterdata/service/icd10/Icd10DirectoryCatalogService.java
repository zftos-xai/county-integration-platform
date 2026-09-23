package cn.zqkj.platform.masterdata.service.icd10;

import cn.zqkj.platform.masterdata.domain.icd10.dto.Icd10DirectoryQuery;
import cn.zqkj.platform.masterdata.domain.icd10.vo.Icd10DirectoryPageVO;

/** 查询平台公共ICD10当前目录的只读业务边界。 */
public interface Icd10DirectoryCatalogService {
    /**
     * 分页读取已发布的公共目录。
     *
     * @param query 有界查询条件
     * @return 当前公共目录分页结果
     */
    Icd10DirectoryPageVO findPage(Icd10DirectoryQuery query);
}
