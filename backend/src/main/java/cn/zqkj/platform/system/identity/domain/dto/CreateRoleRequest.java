package cn.zqkj.platform.system.identity.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 承载创建平台角色请求。
 *
 * @param roleCode 稳定角色代码
 * @param roleName 角色名称
 */
public record CreateRoleRequest(
        @NotBlank @Size(min = 3, max = 64) String roleCode,
        @NotBlank @Size(max = 100) String roleName
) {
}
