package cn.zqkj.platform.masterdata.service.hospitaldirectory.impl;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryRecord;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectoryCountVO;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectoryItemVO;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectoryPageVO;
import cn.zqkj.platform.masterdata.mapper.hospitaldirectory.HospitalDirectoryCatalogMapper;
import cn.zqkj.platform.masterdata.service.hospitaldirectory.HospitalDirectoryCatalogService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 实现正式医院综合目录的分页、SQL机构范围过滤和来源链路组装。 */
@Service
public class HospitalDirectoryCatalogServiceImpl implements HospitalDirectoryCatalogService {

    private final HospitalDirectoryCatalogMapper mapper;

    /**
     * 创建医院综合目录查询服务。
     *
     * @param mapper 当前有效目录只读持久化边界
     */
    public HospitalDirectoryCatalogServiceImpl(HospitalDirectoryCatalogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询指定机构范围内的当前有效医院综合目录及分类数量。
     *
     * <p>仅读取已同步数据；入口负责查询参数与机构授权，SQL 始终限制机构范围。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public HospitalDirectoryPageVO findPage(HospitalDirectoryQuery query, List<String> allowedOrganizationCodes) {
        List<String> organizationCodes = List.copyOf(allowedOrganizationCodes);
        long total = mapper.countPage(query, organizationCodes);
        List<HospitalDirectoryItemVO> items = new ArrayList<>();
        for (HospitalDirectoryRecord record : mapper.findPage(query, organizationCodes)) {
            items.add(toView(record));
        }
        List<HospitalDirectoryCountVO> counts = mapper.countByType(query.organizationCode(), organizationCodes);
        return new HospitalDirectoryPageVO(items, counts, total, query.page(), query.pageSize());
    }

    /**
     * 将数据库目录投影转换为API输出。
     *
     * @param record 当前有效目录记录
     * @return 对外只读视图
     */
    private HospitalDirectoryItemVO toView(HospitalDirectoryRecord record) {
        return new HospitalDirectoryItemVO(
                record.id(), record.organizationCode(), record.organizationName(), record.directoryType(),
                record.sourceRecordCode(), record.sourceRecordName(), record.mnemonicCode(), record.categoryName(),
                record.remark(), record.sourceOrganizationCode(), record.relationCount(), record.latestBatchId(),
                record.latestBatchNo(), Func.toOffset(record.lastSeenAt())
        );
    }
}
