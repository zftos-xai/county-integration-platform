package cn.zqkj.platform.masterdata.service.icd10.impl;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.masterdata.domain.icd10.dto.Icd10DirectoryQuery;
import cn.zqkj.platform.masterdata.domain.icd10.vo.Icd10DirectoryItemVO;
import cn.zqkj.platform.masterdata.domain.icd10.vo.Icd10DirectoryPageVO;
import cn.zqkj.platform.masterdata.mapper.icd10.Icd10DirectoryCatalogMapper;
import cn.zqkj.platform.masterdata.service.icd10.Icd10DirectoryCatalogService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 实现平台公共ICD10目录的分页和来源批次追溯。 */
@Service
public class Icd10DirectoryCatalogServiceImpl implements Icd10DirectoryCatalogService {
    private final Icd10DirectoryCatalogMapper mapper;
    /** @param mapper 公共ICD10目录只读持久化边界。 */
    public Icd10DirectoryCatalogServiceImpl(Icd10DirectoryCatalogMapper mapper) { this.mapper = mapper; }
    /** {@inheritDoc} */
    @Override @Transactional(readOnly = true)
    public Icd10DirectoryPageVO findPage(Icd10DirectoryQuery query) {
        List<Icd10DirectoryItemVO> items = mapper.findPage(query).stream().map(record -> new Icd10DirectoryItemVO(
                record.id(), record.diagnosisCategory(), record.diseaseCode(), record.diseaseName(), record.mnemonicCode(),
                record.remark(), record.sourceCreatedAt(), record.sourceDiseaseId(), record.latestBatchId(),
                record.latestBatchNo(), Func.toOffset(record.lastSeenAt()))).toList();
        return new Icd10DirectoryPageVO(items, mapper.countByCategory(), mapper.countPage(query), query.page(), query.pageSize());
    }
}
