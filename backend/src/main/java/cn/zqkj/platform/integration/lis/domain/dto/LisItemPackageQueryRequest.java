package cn.zqkj.platform.integration.lis.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 县医院机器调用LIS 600-001项目包查询的入站字段。
 *
 * @param organizationCode 平台机构路由代码
 */
public record LisItemPackageQueryRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+") String organizationCode
) {
}
