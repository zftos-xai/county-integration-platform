package cn.zqkj.platform.system.organization.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 定义机构启用状态修改请求。
 *
 * @param enabled 目标启用状态
 * @param version Base64编码的SQL Server并发版本
 */
public record ChangeOrganizationEnabledRequest(
        @NotNull Boolean enabled,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9+/]{11}=") String version
) {
}
