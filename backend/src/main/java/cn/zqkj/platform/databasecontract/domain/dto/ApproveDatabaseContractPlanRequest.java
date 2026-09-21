package cn.zqkj.platform.databasecontract.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 审批数据库契约维护方案请求。
 *
 * @param version 最近读取的Base64行版本
 * @param note 审批依据、执行窗口和回退条件摘要
 */
public record ApproveDatabaseContractPlanRequest(
        @NotBlank String version,
        @NotBlank @Size(max = 500) String note) {
}
