package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 承载替换用户角色请求。
 *
 * @param roleIds 目标角色主键
 */
public record ReplaceRoleIdsRequest(@NotNull List<@Valid @Positive Long> roleIds) {
}
