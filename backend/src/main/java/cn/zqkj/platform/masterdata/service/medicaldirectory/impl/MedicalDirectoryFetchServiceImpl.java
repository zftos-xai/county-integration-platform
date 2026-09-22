package cn.zqkj.platform.masterdata.service.medicaldirectory.impl;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryValidationResult;

import cn.zqkj.platform.his.domain.medicaldirectory.dto.MedicalDirectoryCountQuery;
import cn.zqkj.platform.his.domain.medicaldirectory.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.his.domain.medicaldirectory.model.MedicalDirectoryEntry;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisBusinessException;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.exchange.domain.model.ExchangeResult;
import cn.zqkj.platform.exchange.domain.model.ExchangeRuntimeRecord;
import cn.zqkj.platform.exchange.service.ExchangeRuntimeRecordService;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.his.domain.protocol.model.PhisTrade;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceEntry;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.SourcePagination;
import cn.zqkj.platform.masterdata.service.medicaldirectory.MedicalDirectoryFetchService;
import cn.zqkj.platform.masterdata.service.medicaldirectory.MedicalDirectoryValidationService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 实现100-005声明数与100-004全页取得的一致性边界。 */
@Service
public class MedicalDirectoryFetchServiceImpl implements MedicalDirectoryFetchService {

    private static final int PAGE_SIZE = 100;
    private final PhisService phisService;
    private final MedicalDirectoryValidationService validationService;
    private final ExchangeRuntimeRecordService exchangeRecords;
    private final ZoneId queryZone;

    /**
     * 创建医疗目录完整取数服务。
     *
     * @param phisService 强类型HIS调用服务
     * @param validationService 目录自动校验服务
     * @param exchangeRecords 保存每次数量或分页调用的脱敏事实
     * @param queryZone HIS无时区查询字段使用的医院时区
     */
    public MedicalDirectoryFetchServiceImpl(
            PhisService phisService,
            MedicalDirectoryValidationService validationService,
            ExchangeRuntimeRecordService exchangeRecords,
            @Value("${platform.his.query-zone:Asia/Shanghai}") String queryZone
    ) {
        this.phisService = phisService;
        this.validationService = validationService;
        this.exchangeRecords = exchangeRecords;
        this.queryZone = ZoneId.of(queryZone);
    }

    /**
     * 取得指定机构、目录类型和时间范围内的完整医疗目录。
     *
     * <p>先用100-005确认总数，再用完全相同的范围调用100-004逐页取得数据；任一页失败即停止，
     * 不返回可能被误当成完整结果的部分数据。</p>
     */
    @Override
    public MedicalDirectoryValidationResult fetchAll(
            MasterDataBatchSnapshot batch, MedicalDirectoryType directoryType
    ) {
        // 批次表保存UTC；HIS协议不带偏移，不能把UTC钟面直接当医院本地时间发送。
        LocalDateTime rangeStart = batch.rangeStart().atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(queryZone).toLocalDateTime();
        LocalDateTime rangeEnd = batch.rangeEnd().atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(queryZone).toLocalDateTime();
        MedicalDirectoryCountQuery countQuery = new MedicalDirectoryCountQuery(
                hisType(directoryType), null, rangeStart, rangeEnd, batch.sourceOrganizationId());
        long declaredCount = requireCount(invokeRecorded(batch, directoryType, PhisTrade.MEDICAL_DIRECTORY_COUNT,
                "数量查询", () -> phisService.countMedicalDirectory(batch.organizationId(), batch.environment(), countQuery)));
        List<MedicalDirectorySourceRecord> records = new ArrayList<>();
        for (var page : SourcePagination.plan(declaredCount, PAGE_SIZE)) {
            MedicalDirectoryQuery pageQuery = new MedicalDirectoryQuery(hisType(directoryType), null,
                    page.startRow(), page.endRow(), rangeStart, rangeEnd, batch.sourceOrganizationId());
            PhisResponse<List<MedicalDirectoryEntry>> response = invokeRecorded(batch, directoryType,
                    PhisTrade.MEDICAL_DIRECTORY_QUERY, "行范围" + page.startRow() + "—" + page.endRow(),
                    () -> phisService.queryMedicalDirectory(batch.organizationId(), batch.environment(), pageQuery));
            if (response.data().size() != page.endRow() - page.startRow() + 1) {
                throw new PhisBusinessException("100-004分页条数与请求范围不符；已停止该类型，未更新当前目录");
            }
            for (MedicalDirectoryEntry entry : response.data()) {
                records.add(new MedicalDirectorySourceRecord(directoryType, toSourceEntry(entry)));
            }
        }
        return validationService.validate(declaredCount, records);
    }

    /**
     * 从100-005成功响应中读取非负声明数量。
     *
     * @param response 来源数量响应
     * @return 非负声明数
     */
    private long requireCount(PhisResponse<Long> response) {
        if (response.data() == null || response.data() < 0) {
            throw new PhisProtocolException("基层HIS未返回有效目录行数");
        }
        return response.data();
    }

    /**
     * 将平台业务分类转换为当前HIS协议参数。
     *
     * @param type 平台目录分类
     * @return HIS请求所需目录分类
     */
    private cn.zqkj.platform.his.domain.medicaldirectory.model.MedicalDirectoryType hisType(
            MedicalDirectoryType type
    ) {
        return cn.zqkj.platform.his.domain.medicaldirectory.model.MedicalDirectoryType.valueOf(type.name());
    }

    /**
     * 将HIS报文条目收敛为平台基础数据所需的来源事实。
     *
     * @param entry HIS返回条目
     * @return 不携带协议模型的基础数据来源事实
     */
    private MedicalDirectorySourceEntry toSourceEntry(MedicalDirectoryEntry entry) {
        return new MedicalDirectorySourceEntry(entry.directoryCode(), entry.directoryName(), entry.mnemonicCode(),
                entry.categoryName(), entry.unit(), entry.specification(), entry.dosageForm(), entry.manufacturerName(),
                entry.remark(), entry.sourceCreatedAt(), entry.packageUnit(), entry.conversionFactor(),
                entry.approvalNumber(), entry.standardCode(), entry.packageMaterial(), entry.processingMethod(),
                entry.region(), entry.category(), entry.enabledFlag());
    }

    /** 每次真实调用单独留存交换事实；失败只保存受控分类，不保存HIS任意错误正文或凭证。 */
    private <T> PhisResponse<T> invokeRecorded(MasterDataBatchSnapshot batch, MedicalDirectoryType type,
            PhisTrade trade, String range, Supplier<PhisResponse<T>> invocation) {
        String requestId = Func.simpleUuid();
        LocalDateTime receivedAt = LocalDateTime.now(ZoneOffset.UTC);
        long startedAt = System.nanoTime();
        PhisResponse<T> response;
        try {
            response = invocation.get();
        } catch (PhisCommunicationException exception) {
            exchangeRecords.record(new ExchangeRuntimeRecord(requestId, trade.code(), "PLATFORM", "PRIMARY_HIS",
                    batch.organizationCode(), batch.batchNo(), ExchangeResult.NO_RESPONSE, null,
                    "HIS查询结果未知", (System.nanoTime() - startedAt) / 1_000_000,
                    type.displayName() + "；" + range, "通信失败，未取得可确认的HIS响应",
                    receivedAt, LocalDateTime.now(ZoneOffset.UTC)));
            throw new PhisProtocolException(trade.code() + "结果未知；交易号" + requestId, exception);
        } catch (PhisProtocolException exception) {
            exchangeRecords.record(new ExchangeRuntimeRecord(requestId, trade.code(), "PLATFORM", "PRIMARY_HIS",
                    batch.organizationCode(), batch.batchNo(), ExchangeResult.INVALID_RESPONSE, null,
                    "已收到HIS响应，但报文无法确认业务结果", (System.nanoTime() - startedAt) / 1_000_000,
                    type.displayName() + "；" + range, null,
                    receivedAt, LocalDateTime.now(ZoneOffset.UTC)));
            throw new PhisProtocolException(trade.code() + "响应不符合协议；交易号" + requestId, exception);
        }
        if (response == null) {
            exchangeRecords.record(new ExchangeRuntimeRecord(requestId, trade.code(), "PLATFORM", "PRIMARY_HIS",
                    batch.organizationCode(), batch.batchNo(), ExchangeResult.INVALID_RESPONSE, null,
                    "HIS调用未提供可确认的响应对象", (System.nanoTime() - startedAt) / 1_000_000,
                    type.displayName() + "；" + range, null,
                    receivedAt, LocalDateTime.now(ZoneOffset.UTC)));
            throw new PhisProtocolException(trade.code() + "缺少有效响应；交易号" + requestId);
        }
        String summary = response.success() ? "HIS查询成功" : safeBusinessFailure(response.errorMessage());
        exchangeRecords.record(new ExchangeRuntimeRecord(requestId, trade.code(), "PLATFORM", "PRIMARY_HIS",
                batch.organizationCode(), batch.batchNo(), response.success() ? ExchangeResult.SUCCESS : ExchangeResult.FAILURE,
                response.resultCode(), summary, (System.nanoTime() - startedAt) / 1_000_000,
                type.displayName() + "；" + range, null, receivedAt, LocalDateTime.now(ZoneOffset.UTC)));
        if (!response.success()) {
            throw new PhisBusinessException(trade.code() + "失败（结果码" + response.resultCode()
                    + "）：" + summary + "；交易号" + requestId);
        }
        return response;
    }

    private String safeBusinessFailure(String message) {
        if (message != null && (message.contains("无权访问") || message.contains("授权已过期")
                || message.contains("申请机构授权"))) {
            return "HIS机构授权未通过，请核对交易机构编码与授权配置";
        }
        if (message != null && (message.contains("未将对象引用设置") || message.contains("Object reference"))) {
            return "HIS处理请求时发生空引用错误，请核对交易字段与来源服务";
        }
        return "HIS明确拒绝查询，未返回可公开展示的错误分类；请凭交易号核查来源记录";
    }

}
