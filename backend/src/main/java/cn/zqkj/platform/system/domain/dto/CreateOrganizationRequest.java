package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * 定义创建平台机构的HTTP请求。
 *
 * @param organizationCode 平台统一机构编码
 * @param organizationName 机构名称
 * @param organizationType 经确认的机构类型代码
 * @param parentId 可选父机构主键
 * @param validFrom 可选有效起始时间
 * @param validTo 可选有效结束时间
 */
public record CreateOrganizationRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+") String organizationCode,
        @NotBlank @Size(max = 200) String organizationName,
        @NotBlank @Size(max = 32) String organizationType,
        Long parentId,
        OffsetDateTime validFrom,
        OffsetDateTime validTo
) {
}
