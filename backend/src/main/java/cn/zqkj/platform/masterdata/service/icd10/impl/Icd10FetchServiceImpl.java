package cn.zqkj.platform.masterdata.service.icd10.impl;

import cn.zqkj.platform.his.domain.icd10.dto.Icd10CountQuery;
import cn.zqkj.platform.his.domain.icd10.dto.Icd10Query;
import cn.zqkj.platform.his.domain.icd10.model.Icd10Entry;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.domain.protocol.model.PhisTrade;
import cn.zqkj.platform.his.exception.PhisBusinessException;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.exchange.domain.model.ExchangeResult;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10DiagnosisCategory;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10HisInvocation;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10SourceEntry;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10SourceRecord;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10ValidationResult;
import cn.zqkj.platform.masterdata.service.icd10.Icd10FetchService;
import cn.zqkj.platform.masterdata.service.icd10.Icd10ValidationService;
import cn.zqkj.platform.masterdata.mapper.icd10.Icd10SyncMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 以同一时间范围调用100-007和100-006，取得一个公共诊断类别的完整结果。 */
@Service
public class Icd10FetchServiceImpl implements Icd10FetchService {

    private static final int PAGE_SIZE = 100;
    private final PhisService phisService;
    private final Icd10ValidationService validationService;
    private final Icd10SyncMapper mapper;
    private final ZoneId queryZone;

    /**
     * 创建公共ICD10来源取得服务。
     *
     * @param phisService 强类型HIS调用边界
     * @param validationService 来源质量与数量核对边界
     * @param mapper ICD10调用事实持久化边界
     * @param queryZone HIS无时区时间字段采用的医院时区
     */
    public Icd10FetchServiceImpl(PhisService phisService, Icd10ValidationService validationService, Icd10SyncMapper mapper,
                                 @Value("${platform.his.query-zone:Asia/Shanghai}") String queryZone) {
        this.phisService = phisService;
        this.validationService = validationService;
        this.mapper = mapper;
        this.queryZone = ZoneId.of(queryZone);
    }

    /** {@inheritDoc} */
    @Override
    public Icd10ValidationResult fetchAll(
            MasterDataBatchSnapshot batch, long endpointOrganizationId, Icd10DiagnosisCategory category) {
        if (batch.rangeStart() == null || batch.rangeEnd() == null) {
            throw new IllegalArgumentException("ICD10公共批次缺少来源查询时间范围");
        }
        LocalDateTime start = batch.rangeStart().atOffset(ZoneOffset.UTC).atZoneSameInstant(queryZone).toLocalDateTime();
        LocalDateTime end = batch.rangeEnd().atOffset(ZoneOffset.UTC).atZoneSameInstant(queryZone).toLocalDateTime();
        var hisCategory = cn.zqkj.platform.his.domain.icd10.model.Icd10DiagnosisCategory.valueOf(category.name());
        int invocationSequence = 1;
        Icd10CountQuery countQuery = new Icd10CountQuery(null, start, end, hisCategory, null);
        long declared = requireCount(invoke(batch, category, invocationSequence++, PhisTrade.ICD10_COUNT.code(), null, null,
                requestSummary(category, start, end, null, null),
                () -> phisService.countIcd10(endpointOrganizationId, batch.environment(), countQuery), value -> value));
        List<Icd10SourceRecord> records = new ArrayList<>();
        for (long startRow = 1; startRow <= declared; startRow += PAGE_SIZE) {
            // TEST端点实测请求1-100仅返回99条；其结束行按不包含边界处理。
            long endRow = Math.min(declared + 1, startRow + PAGE_SIZE);
            Icd10Query pageQuery = new Icd10Query(null, startRow, endRow, start, end, hisCategory, null);
            PhisResponse<List<Icd10Entry>> response = invoke(batch, category, invocationSequence++, PhisTrade.ICD10_QUERY.code(),
                    startRow, endRow, requestSummary(category, start, end, startRow, endRow),
                    () -> phisService.queryIcd10(endpointOrganizationId, batch.environment(), pageQuery),
                    value -> value == null ? null : (long) value.size());
            if (!response.success()) {
                throw new PhisBusinessException("100-006查询失败（结果码" + response.resultCode() + "）");
            }
            if (response.data() == null || response.data().size() != endRow - startRow) {
                int received = response.data() == null ? -1 : response.data().size();
                throw new PhisProtocolException("100-006分页条数与请求范围不符：声明总数" + declared
                        + "，请求行" + startRow + "-" + endRow + "，实际返回" + received + "；未更新公共ICD10目录");
            }
            for (Icd10Entry entry : response.data()) {
                records.add(new Icd10SourceRecord(category, new Icd10SourceEntry(entry.diseaseCode(), entry.diseaseName(),
                        entry.mnemonicCode(), entry.remark(), entry.sourceCreatedAt(), entry.sourceDiseaseId())));
            }
        }
        return validationService.validate(declared, records);
    }

    /**
     * 执行一次ICD10来源调用并将脱敏输入和最终结果作为独立事实写入。
     *
     * <p>调用记录写入失败会终止本类别处理，避免把“可更新当前目录”误报为“可完整追溯”。
     * 通信失败和协议错误同样留存事实，但不吞没原始受控异常。</p>
     */
    private <T> PhisResponse<T> invoke(
            MasterDataBatchSnapshot batch, Icd10DiagnosisCategory category, int sequence, String tradeCode,
            Long pageStart, Long pageEnd, String requestSummary, Supplier<PhisResponse<T>> invocation,
            Function<T, Long> returnedCount
    ) {
        LocalDateTime requestedAt = LocalDateTime.now(ZoneOffset.UTC);
        long startedAt = System.nanoTime();
        try {
            PhisResponse<T> response = invocation.get();
            if (response == null) {
                throw new PhisProtocolException(tradeCode + "未返回有效响应");
            }
            Long count = response.success() ? returnedCount.apply(response.data()) : null;
            saveInvocation(batch, category, sequence, tradeCode, pageStart, pageEnd, requestSummary,
                    response.success() ? ExchangeResult.SUCCESS : ExchangeResult.FAILURE, response.resultCode(),
                    response.success() ? successSummary(tradeCode, count) : "HIS明确返回失败", count, startedAt, requestedAt);
            return response;
        } catch (PhisCommunicationException exception) {
            saveInvocation(batch, category, sequence, tradeCode, pageStart, pageEnd, requestSummary,
                    ExchangeResult.NO_RESPONSE, null, "通信失败或超时，未取得可确认的HIS响应", null, startedAt, requestedAt);
            throw exception;
        } catch (PhisProtocolException exception) {
            saveInvocation(batch, category, sequence, tradeCode, pageStart, pageEnd, requestSummary,
                    ExchangeResult.INVALID_RESPONSE, null, "已收到HIS响应，但无法确认协议结果", null, startedAt, requestedAt);
            throw exception;
        }
    }

    /** 保存不含完整报文和认证数据的一次调用终态。 */
    private void saveInvocation(
            MasterDataBatchSnapshot batch, Icd10DiagnosisCategory category, int sequence, String tradeCode,
            Long pageStart, Long pageEnd, String requestSummary, ExchangeResult status, String resultCode,
            String responseSummary, Long returnedCount, long startedAt, LocalDateTime requestedAt
    ) {
        int inserted = mapper.insertHisInvocation(new Icd10HisInvocation(batch.id(), category, sequence, tradeCode,
                pageStart, pageEnd, requestSummary, status, resultCode, responseSummary, returnedCount,
                (System.nanoTime() - startedAt) / 1_000_000, requestedAt, LocalDateTime.now(ZoneOffset.UTC)));
        if (inserted != 1) throw new IllegalStateException("ICD10 HIS调用追踪未写入唯一事实");
    }

    /** 构造页面和审计可展示的请求参数摘要，不包含端点和认证信息。 */
    private String requestSummary(Icd10DiagnosisCategory category, LocalDateTime start, LocalDateTime end,
                                  Long pageStart, Long pageEnd) {
        String summary = "诊断类别=" + category.code() + "；开始时间=" + start + "；结束时间=" + end;
        return pageStart == null ? summary : summary + "；行范围=" + pageStart + "-" + pageEnd;
    }

    /** 构造不携带来源正文的成功响应摘要。 */
    private String successSummary(String tradeCode, Long returnedCount) {
        return tradeCode + "成功；" + (returnedCount == null ? "数量未提供" : "数量=" + returnedCount);
    }

    /** 从100-007成功响应中读取非负声明数。 */
    private long requireCount(PhisResponse<Long> response) {
        if (response == null || !response.success()) {
            throw new PhisBusinessException("100-007数量查询未得到可确认的成功结果");
        }
        if (response.data() == null || response.data() < 0) {
            throw new PhisProtocolException("100-007未返回有效的非负行数");
        }
        return response.data();
    }
}
