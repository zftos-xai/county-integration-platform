package cn.zqkj.platform.exchange.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.exchange.domain.model.ExchangeResult;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 接收指定机构的交换记录查询参数，保留输入时间的偏移量。
 *
 * @param organizationCode 必填平台机构编码
 * @param receivedFrom 可选带偏移量的接收时间下界
 * @param receivedTo 可选带偏移量的接收时间上界
 * @param interfaceCode 可选接口事件码
 * @param sourceRecordId 可选业务记录引用
 * @param requestId 可选平台请求编号
 * @param result 可选交换结果
 * @param limit 最大返回条数，省略时为20
 */
public record ExchangeRecordQuery(
        @NotBlank @Size(max = 64) String organizationCode,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime receivedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime receivedTo,
        @Size(max = 64) String interfaceCode,
        @Size(max = 128) String sourceRecordId,
        @Size(max = 64) String requestId,
        ExchangeResult result,
        @Min(1) @Max(100) Integer limit
) {

    /**
     * 统一筛选文本并补齐默认条数，约束由入口的Bean Validation执行。
     *
     * @param organizationCode 必填平台机构编码
     * @param receivedFrom 可选带偏移量的接收时间下界
     * @param receivedTo 可选带偏移量的接收时间上界
     * @param interfaceCode 可选接口事件码
     * @param sourceRecordId 可选业务记录引用
     * @param requestId 可选平台请求编号
     * @param result 可选交换结果
     * @param limit 最大返回条数，省略时为20
     */
    public ExchangeRecordQuery {
        organizationCode = Func.trimToNull(organizationCode);
        interfaceCode = Func.trimToNull(interfaceCode);
        sourceRecordId = Func.trimToNull(sourceRecordId);
        requestId = Func.trimToNull(requestId);
        limit = limit == null ? 20 : limit;
    }

    /**
     * 检查查询起止时间的先后关系。
     * @return 未限定区间或下界不晚于上界时为true
     */
    @AssertTrue(message = "开始时间不能晚于结束时间")
    public boolean isTimeRangeValid() {
        return receivedFrom == null || receivedTo == null || !receivedFrom.isAfter(receivedTo);
    }

    /**
     * 将查询时间转换为SQL Server存储使用的UTC时间。
     * @return UTC时间；未限定时为空
     */
    public LocalDateTime receivedFromUtc() {
        return Func.toUtc(receivedFrom);
    }

    /**
     * 将查询时间转换为SQL Server存储使用的UTC时间。
     * @return UTC时间；未限定时为空
     */
    public LocalDateTime receivedToUtc() {
        return Func.toUtc(receivedTo);
    }
}
