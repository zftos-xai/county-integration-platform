package cn.zqkj.platform.system.identity.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 承载修改平台用户基础资料请求。
 *
 * @param displayName 显示名称
 * @param primaryOrganizationId 主机构主键
 * @param version Base64编码并发版本
 */
public record UpdateUserRequest(
        @NotBlank @Size(max = 100) String displayName,
        @Positive long primaryOrganizationId,
        @NotBlank String version
) {
}
