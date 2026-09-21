package cn.zqkj.platform.databasecontract.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建数据库契约维护方案请求。
 *
 * @param issueKeys 扫描结果返回的稳定差异键
 * @param summary 方案摘要
 */
public record CreateDatabaseContractPlanRequest(
        @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 400) String> issueKeys,
        @NotBlank @Size(max = 500) String summary) {
}
