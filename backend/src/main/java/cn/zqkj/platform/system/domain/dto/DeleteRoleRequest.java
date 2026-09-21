package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 使用行版本删除角色的API输入。
 *
 * @param version Base64编码并发版本
 */
public record DeleteRoleRequest(@NotBlank String version) {
}
