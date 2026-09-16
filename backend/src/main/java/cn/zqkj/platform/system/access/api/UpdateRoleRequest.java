package cn.zqkj.platform.system.access.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 承载修改非系统角色请求。
 *
 * @param roleName 角色名称
 * @param enabled 是否启用
 * @param version Base64编码并发版本
 */
public record UpdateRoleRequest(
        @NotBlank @Size(max = 100) String roleName,
        boolean enabled,
        @NotBlank String version
) {
}
