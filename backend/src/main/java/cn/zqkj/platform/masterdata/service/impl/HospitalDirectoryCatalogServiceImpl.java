package cn.zqkj.platform.masterdata.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.masterdata.domain.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.model.HospitalDirectoryRecord;
import cn.zqkj.platform.masterdata.domain.vo.HospitalDirectoryCountVO;
import cn.zqkj.platform.masterdata.domain.vo.HospitalDirectoryItemVO;
import cn.zqkj.platform.masterdata.domain.vo.HospitalDirectoryPageVO;
import cn.zqkj.platform.masterdata.mapper.HospitalDirectoryCatalogMapper;
import cn.zqkj.platform.masterdata.service.HospitalDirectoryCatalogService;
import cn.zqkj.platform.system.domain.model.AccessActor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

/** 实现正式医院综合目录的分页、机构范围控制和来源链路组装。 */
@Service
public class HospitalDirectoryCatalogServiceImpl implements HospitalDirectoryCatalogService {

    private static final int MAXIMUM_PAGE_SIZE = 100;
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
     * {@inheritDoc}
     *
     * <p>查询前规范化分页条件并校验机构数据范围，返回结果不会越过当前操作人的机构授权。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public HospitalDirectoryPageVO findPage(HospitalDirectoryQuery rawQuery, AccessActor actor) {
        HospitalDirectoryQuery query = normalize(rawQuery);
        if (query.organizationCode() != null && !actor.canAccess(query.organizationCode())) {
            throw new AccessDeniedException("当前账号无权访问该机构");
        }
        var organizationCodes = new ArrayList<>(actor.organizationCodes());
        long total = mapper.countPage(query, organizationCodes);
        var items = mapper.findPage(query, organizationCodes).stream().map(this::toView).toList();
        var counts = mapper.countByType(query.organizationCode(), organizationCodes).stream()
                .map(item -> new HospitalDirectoryCountVO(item.directoryType(), item.total()))
                .toList();
        return new HospitalDirectoryPageVO(items, counts, total, query.page(), query.pageSize());
    }

    /**
     * 裁剪可选文本并把空白统一为无值。
     *
     * @param rawQuery 原始查询
     * @return 已规范化查询
     */
    private HospitalDirectoryQuery normalize(HospitalDirectoryQuery rawQuery) {
        if (rawQuery == null || rawQuery.page() < 1 || rawQuery.pageSize() < 1
                || rawQuery.pageSize() > MAXIMUM_PAGE_SIZE) {
            throw new InvalidRequestException("分页参数不符合要求");
        }
        return new HospitalDirectoryQuery(
                Func.trimToNull(rawQuery.organizationCode()),
                rawQuery.directoryType(),
                Func.trimToNull(rawQuery.keyword()),
                rawQuery.page(),
                rawQuery.pageSize()
        );
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
