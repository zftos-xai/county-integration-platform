package cn.zqkj.platform.databasecontract.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 数据库契约维护方案的审批、执行或取消请求。
 *
 * @param version 最近读取的Base64行版本
 */
public record AdvanceDatabaseContractPlanRequest(@NotBlank String version) {
}
