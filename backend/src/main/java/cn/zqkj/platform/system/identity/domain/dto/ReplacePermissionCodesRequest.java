package cn.zqkj.platform.system.identity.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 承载替换角色功能权限请求。
 *
 * @param permissionCodes 目标代码注册权限
 */
public record ReplacePermissionCodesRequest(
        @NotNull List<@NotBlank String> permissionCodes
) {
}
