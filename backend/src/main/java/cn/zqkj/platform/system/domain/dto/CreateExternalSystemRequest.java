package cn.zqkj.platform.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建外部系统登记信息的API输入。
 *
 * @param systemCode 稳定代码
 * @param systemName 名称
 * @param description 用途说明
 */
public record CreateExternalSystemRequest(@NotBlank @Size(max = 64) String systemCode,
                                          @NotBlank @Size(max = 100) String systemName,
                                          @NotBlank @Size(max = 500) String description) {
}
