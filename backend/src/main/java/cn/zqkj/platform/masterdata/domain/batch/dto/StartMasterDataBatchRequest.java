package cn.zqkj.platform.masterdata.domain.batch.dto;

import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * 接收一次范围明确的基础数据同步请求。
 *
 * <p>100-004/100-005目录同步必须携带来源接口确认的同一查询时间范围；服务端不会猜测时间游标。</p>
 *
 * @param requestKey 一次用户操作的稳定请求标识，用于结果未知后回读
 * @param organizationCode 平台机构代码；平台公共目录为空
 * @param environment 调用机构接口时使用的明确运行环境
 * @param category 基础数据类别
 * @param rangeStart 医疗目录来源查询开始时间；非医疗目录为空
 * @param rangeEnd 医疗目录来源查询结束时间；非医疗目录为空
 */
public record StartMasterDataBatchRequest(
        @NotBlank @Size(min = 20, max = 64) String requestKey,
        @Size(max = 64) String organizationCode,
        @NotNull ParameterEnvironment environment,
        @NotNull MasterDataCategory category,
        OffsetDateTime rangeStart,
        OffsetDateTime rangeEnd
) {

    /**
     * 兼容已有100-003调用方创建不需要来源时间范围的同步请求。
     *
     * @param requestKey 一次用户操作的稳定请求标识
     * @param organizationCode 平台机构代码
     * @param environment 调用机构接口时使用的运行环境
     * @param category 基础数据类别
     */
    public StartMasterDataBatchRequest(
            String requestKey,
            String organizationCode,
            ParameterEnvironment environment,
            MasterDataCategory category
    ) {
        this(requestKey, organizationCode, environment, category, null, null);
    }
}
