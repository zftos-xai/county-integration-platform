package cn.zqkj.platform.masterdata.domain.batch.dto;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataSyncMode;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * 接收一次指定同步模式的基础数据同步请求。
 *
 * <p>按时间查询使用调用人指定的范围；全量模式由服务端固定为批次创建时刻向前20年。</p>
 *
 * <p>写入目标：{@code dbo.md_sync_batch}。业务说明：记录同步条件和幂等标识；类别决定交易，来源配置决定机构标识，运行状态由服务维护，此输入不是完整表行。</p>
 *
 * @param requestKey 一次用户操作的稳定请求标识，用于结果未知后回读
 * @param organizationCode 机构目录归属机构代码；公共ICD10必须为空
 * @param sourceEndpointOrganizationCode 公共ICD10调用端点所属机构代码；不表示目录归属
 * @param environment 调用机构接口时使用的明确运行环境
 * @param category 基础数据类别
 * @param mode 医疗目录的近20年全量或指定范围模式；公共ICD10只允许指定范围
 * @param rangeStart 医疗目录来源查询开始时间；医院综合目录为空
 * @param rangeEnd 医疗目录来源查询结束时间；医院综合目录为空
 */
public record StartMasterDataBatchRequest(
        @NotBlank @Size(min = 20, max = 64) String requestKey,
        @Size(max = 64) String organizationCode,
        @Size(max = 64) String sourceEndpointOrganizationCode,
        @NotNull ParameterEnvironment environment,
        @NotNull MasterDataCategory category,
        @NotNull MasterDataSyncMode mode,
        OffsetDateTime rangeStart,
        OffsetDateTime rangeEnd
) {

    /**
     * 统一请求标识和机构代码的首尾空白，不改变同步范围。
     *
     * @param requestKey 一次用户操作的稳定请求标识，用于结果未知后回读
     * @param organizationCode 机构目录的归属机构代码；公共ICD10必须为空
     * @param sourceEndpointOrganizationCode 公共ICD10调用端点所属机构代码；只用于端点授权，不是目录归属
     * @param environment 调用机构接口时使用的明确运行环境
     * @param category 基础数据类别
     * @param mode 医疗目录的全量或指定范围模式；医院综合目录不适用
     * @param rangeStart 医疗目录来源查询开始时间；非医疗目录为空
     * @param rangeEnd 医疗目录来源查询结束时间；非医疗目录为空
     */
    public StartMasterDataBatchRequest {
        requestKey = Func.trimToNull(requestKey);
        organizationCode = Func.trimToNull(organizationCode);
        sourceEndpointOrganizationCode = Func.trimToNull(sourceEndpointOrganizationCode);
    }

    /**
     * 保持既有机构目录调用方的请求构造兼容。
     *
     * @param requestKey 一次用户操作的稳定请求标识
     * @param organizationCode 机构目录归属机构代码
     * @param environment 调用机构接口时使用的运行环境
     * @param category 基础数据类别
     * @param mode 同步模式
     * @param rangeStart 可选来源查询开始时间
     * @param rangeEnd 可选来源查询结束时间
     */
    public StartMasterDataBatchRequest(
            String requestKey, String organizationCode, ParameterEnvironment environment,
            MasterDataCategory category, MasterDataSyncMode mode, OffsetDateTime rangeStart, OffsetDateTime rangeEnd
    ) {
        this(requestKey, organizationCode, null, environment, category, mode, rangeStart, rangeEnd);
    }

    /**
     * 检查模式与调用人可指定的时间字段是否匹配。
     * @return 指定范围完整有序、全量及医院综合目录未夹带范围时为 true
     */
    @AssertTrue(message = "同步模式与来源查询时间范围不匹配")
    public boolean isTimeRangeValid() {
        if (category == null || mode == null) return true;
        if (category == MasterDataCategory.ICD10_DIAGNOSIS) {
            return mode == MasterDataSyncMode.TIME_RANGE
                    && rangeStart != null && rangeEnd != null && !rangeEnd.isBefore(rangeStart);
        }
        if (category == MasterDataCategory.MEDICAL_DIRECTORY) {
            if (mode == MasterDataSyncMode.TIME_RANGE) {
                return rangeStart != null && rangeEnd != null && !rangeEnd.isBefore(rangeStart);
            }
            return mode == MasterDataSyncMode.FULL && rangeStart == null && rangeEnd == null;
        }
        return mode == MasterDataSyncMode.NOT_APPLICABLE && rangeStart == null && rangeEnd == null;
    }

    /**
     * 校验机构归属与调用端点机构不会在公共目录请求中混用。
     *
     * @return 机构目录只指定归属机构，公共ICD10只指定端点机构时为true
     */
    @AssertTrue(message = "目录归属与调用端点机构不匹配")
    public boolean isScopeInputValid() {
        if (category == null) return true;
        if (category == MasterDataCategory.ICD10_DIAGNOSIS) {
            return organizationCode == null && sourceEndpointOrganizationCode != null;
        }
        return organizationCode != null && sourceEndpointOrganizationCode == null;
    }

}
