package cn.zqkj.platform.masterdata.service.medicaldirectory.impl;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryRecord;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectoryCountVO;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectoryItemVO;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectoryPageVO;
import cn.zqkj.platform.masterdata.mapper.medicaldirectory.MedicalDirectoryCatalogMapper;
import cn.zqkj.platform.masterdata.service.medicaldirectory.MedicalDirectoryCatalogService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实现医疗目录的分页、SQL机构范围过滤和来源链路组装。
 */
@Service
public class MedicalDirectoryCatalogServiceImpl implements MedicalDirectoryCatalogService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final MedicalDirectoryCatalogMapper mapper;

    /**
     * 创建医疗目录查询服务。
     *
     * @param mapper 当前有效医疗目录只读持久化边界
     */
    public MedicalDirectoryCatalogServiceImpl(MedicalDirectoryCatalogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询指定机构范围内的当前有效医疗目录及分类数量。
     *
     * <p>仅读取已同步数据；入口负责查询参数与机构授权，SQL 始终限制机构范围。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public MedicalDirectoryPageVO findPage(MedicalDirectoryQuery query, List<String> allowedOrganizationCodes) {
        List<String> organizationCodes = List.copyOf(allowedOrganizationCodes);
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDateTime todayStartUtc = today.atStartOfDay(BUSINESS_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime tomorrowStartUtc = today.plusDays(1).atStartOfDay(BUSINESS_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        long total = mapper.countPage(query, organizationCodes);
        List<MedicalDirectoryItemVO> items = new ArrayList<>();
        for (MedicalDirectoryRecord record : mapper.findPage(query, organizationCodes)) {
            items.add(toView(record, todayStartUtc, tomorrowStartUtc));
        }
        List<MedicalDirectoryCountVO> counts = mapper.countByType(
                query.organizationCode(), organizationCodes, todayStartUtc, tomorrowStartUtc);
        return new MedicalDirectoryPageVO(items, counts, total, query.page(), query.pageSize());
    }

    /**
     * 将数据库投影转换为不暴露内部字段的只读输出。
     *
     * @param record 当前有效医疗目录记录
     * @param todayStartUtc 北京时间今日零点对应的UTC时间
     * @param tomorrowStartUtc 北京时间明日零点对应的UTC时间
     * @return 对外只读视图
     */
    private MedicalDirectoryItemVO toView(MedicalDirectoryRecord record,
                                          LocalDateTime todayStartUtc, LocalDateTime tomorrowStartUtc) {
        boolean newToday = !record.firstSeenAt().isBefore(todayStartUtc)
                && record.firstSeenAt().isBefore(tomorrowStartUtc);
        return new MedicalDirectoryItemVO(
                record.id(), record.organizationCode(), record.organizationName(), record.directoryType(),
                record.sourceRecordCode(), record.sourceRecordName(), record.mnemonicCode(), record.categoryName(),
                record.unit(), record.specification(), record.manufacturerName(), record.sourceEnabledFlag(),
                record.sourceCreatedAt(), record.dosageForm(), record.remark(), record.packageUnit(),
                record.conversionFactor(), record.approvalNumber(), record.standardCode(), record.packageMaterial(),
                record.processingMethod(), record.region(), record.category(), record.latestBatchId(), record.latestBatchNo(),
                record.sourceOrganizationId(), Func.toOffset(record.firstSeenAt()), newToday,
                Func.toOffset(record.lastSeenAt()));
    }
}
