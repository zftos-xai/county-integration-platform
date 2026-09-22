package cn.zqkj.platform.masterdata.domain.batch.dto;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 接收同步批次列表的筛选条件并保持HTTP时间的偏移信息。
 *
 * @param organizationCode 可选平台机构编码
 * @param requestKey 可选请求标识
 * @param category 可选基础数据类别
 * @param status 可选批次状态
 * @param startedFrom 可选带偏移量的开始时间下界
 * @param startedTo 可选带偏移量的开始时间上界
 * @param page 从1开始的页码，省略时为1
 * @param pageSize 每页条数，省略时为20
 */
public record MasterDataBatchQuery(
        @Size(max = 64) String organizationCode,
        @Size(max = 64) String requestKey,
        MasterDataCategory category,
        MasterDataBatchStatus status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startedTo,
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer pageSize
) {

    /**
     * 统一筛选文本并补齐默认条数，约束由入口的Bean Validation执行。
     *
     * @param organizationCode 可选平台机构编码
     * @param requestKey 可选请求标识
     * @param category 可选基础数据类别
     * @param status 可选批次状态
     * @param startedFrom 可选带偏移量的开始时间下界
     * @param startedTo 可选带偏移量的开始时间上界
     * @param page 从1开始的页码，省略时为1
     * @param pageSize 每页条数，省略时为20
     */
    public MasterDataBatchQuery {
        organizationCode = Func.trimToNull(organizationCode);
        requestKey = Func.trimToNull(requestKey);
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }

    /**
     * 检查查询起止时间的先后关系。
     * @return 未限定区间或下界不晚于上界时为true
     */
    @AssertTrue(message = "开始时间不能晚于结束时间")
    public boolean isTimeRangeValid() {
        return startedFrom == null || startedTo == null || !startedFrom.isAfter(startedTo);
    }

    /**
     * 将查询时间转换为SQL Server存储使用的UTC时间。
     * @return UTC时间；未限定时为空
     */
    public LocalDateTime startedFromUtc() {
        return Func.toUtc(startedFrom);
    }

    /**
     * 将查询时间转换为SQL Server存储使用的UTC时间。
     * @return UTC时间；未限定时为空
     */
    public LocalDateTime startedToUtc() {
        return Func.toUtc(startedTo);
    }

    /**
     * 计算SQL Server分页的起始偏移量。
     * @return 从零开始的行偏移量
     */
    public long offset() {
        return (page - 1L) * pageSize;
    }
}
