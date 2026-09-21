package cn.zqkj.platform.masterdata.service.impl;

import cn.zqkj.platform.his.domain.dto.MedicalDirectoryCountQuery;
import cn.zqkj.platform.his.domain.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.his.domain.model.MedicalDirectoryEntry;
import cn.zqkj.platform.his.domain.model.MedicalDirectoryType;
import cn.zqkj.platform.his.domain.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisBusinessException;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.masterdata.domain.model.MedicalDirectoryFetchResult;
import cn.zqkj.platform.masterdata.domain.model.MedicalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.model.SourcePagination;
import cn.zqkj.platform.masterdata.service.MedicalDirectoryFetchService;
import cn.zqkj.platform.masterdata.service.MedicalDirectoryValidationService;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 实现100-005声明数与100-004全页取得的一致性边界。 */
@Service
public class MedicalDirectoryFetchServiceImpl implements MedicalDirectoryFetchService {

    private static final int PAGE_SIZE = 100;
    private final PhisService phisService;
    private final MedicalDirectoryValidationService validationService;

    /**
     * 创建医疗目录完整取数服务。
     *
     * @param phisService 强类型HIS调用服务
     * @param validationService 目录自动校验服务
     */
    public MedicalDirectoryFetchServiceImpl(
            PhisService phisService,
            MedicalDirectoryValidationService validationService
    ) {
        this.phisService = phisService;
        this.validationService = validationService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>先用100-005确认总数，再用完全相同的范围调用100-004逐页取得数据；任一页失败即停止，
     * 不返回可能被误当成完整结果的部分数据。</p>
     */
    @Override
    public MedicalDirectoryFetchResult fetchAll(
            long organizationId,
            ParameterEnvironment environment,
            MedicalDirectoryType directoryType,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            String sourceOrganizationCode
    ) {
        requireArguments(organizationId, environment, directoryType, rangeStart, rangeEnd, sourceOrganizationCode);
        long declaredCount = requireCount(phisService.countMedicalDirectory(organizationId, environment,
                new MedicalDirectoryCountQuery(directoryType, null, rangeStart, rangeEnd, sourceOrganizationCode)));
        List<MedicalDirectorySourceRecord> records = new ArrayList<>();
        for (var page : SourcePagination.plan(declaredCount, PAGE_SIZE)) {
            PhisResponse<List<MedicalDirectoryEntry>> response = phisService.queryMedicalDirectory(
                    organizationId, environment, new MedicalDirectoryQuery(directoryType, null,
                    page.startRow(), page.endRow(), rangeStart, rangeEnd, sourceOrganizationCode));
            if (!response.success()) {
                throw new PhisBusinessException(safeError(response.errorMessage()));
            }
            if (response.data() == null) {
                throw new PhisBusinessException("基层HIS未返回" + directoryType.displayName() + "目录数据");
            }
            response.data().forEach(entry -> records.add(new MedicalDirectorySourceRecord(directoryType, entry)));
        }
        return new MedicalDirectoryFetchResult(directoryType, validationService.validate(declaredCount, records));
    }

    /**
     * 从100-005成功响应中读取非负声明数量。
     *
     * @param response 来源数量响应
     * @return 非负声明数
     */
    private long requireCount(PhisResponse<Long> response) {
        if (response == null || !response.success()) {
            throw new PhisBusinessException(safeError(response == null ? null : response.errorMessage()));
        }
        if (response.data() == null || response.data() < 0) {
            throw new PhisBusinessException("基层HIS未返回有效目录行数");
        }
        return response.data();
    }

    /** 校验不依赖页面的必要业务范围。 */
    private void requireArguments(
            long organizationId,
            ParameterEnvironment environment,
            MedicalDirectoryType directoryType,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            String sourceOrganizationCode
    ) {
        if (organizationId < 1 || environment == null || directoryType == null
                || rangeStart == null || rangeEnd == null || sourceOrganizationCode == null
                || sourceOrganizationCode.isBlank()) {
            throw new IllegalArgumentException("医院目录取得范围不完整");
        }
        if (rangeEnd.isBefore(rangeStart)) {
            throw new IllegalArgumentException("医院目录查询结束时间不能早于开始时间");
        }
    }

    /**
     * 生成不含地址、凭证和报文正文的HIS错误摘要。
     *
     * @param error HIS受控错误
     * @return 可安全记录的简短说明
     */
    private String safeError(String error) {
        if (error == null || error.isBlank()) return "基层HIS未返回可理解的失败原因";
        return error.length() <= 500 ? error : error.substring(0, 500);
    }
}
