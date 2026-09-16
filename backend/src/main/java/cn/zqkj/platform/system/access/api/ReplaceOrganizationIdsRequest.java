package cn.zqkj.platform.system.access.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 承载替换用户机构数据范围请求。
 *
 * @param organizationIds 目标机构主键
 */
public record ReplaceOrganizationIdsRequest(@NotNull List<@Valid @Positive Long> organizationIds) {
}
