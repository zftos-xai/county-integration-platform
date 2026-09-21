package cn.zqkj.platform.masterdata.domain.dto;

import cn.zqkj.platform.masterdata.domain.model.MasterDataCategory;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 接收一次范围明确的基础数据同步请求。
 *
 * <p>当前只开放已形成完整闭环的100-003医院综合目录同步；不能由调用方拼接未来业务参数。</p>
 *
 * @param requestKey 一次用户操作的稳定请求标识，用于结果未知后回读
 * @param organizationCode 平台机构代码；平台公共目录为空
 * @param environment 调用机构接口时使用的明确运行环境
 * @param category 基础数据类别
 */
public record StartMasterDataBatchRequest(
        @NotBlank @Size(min = 20, max = 64) String requestKey,
        @Size(max = 64) String organizationCode,
        @NotNull ParameterEnvironment environment,
        @NotNull MasterDataCategory category
) {
}
