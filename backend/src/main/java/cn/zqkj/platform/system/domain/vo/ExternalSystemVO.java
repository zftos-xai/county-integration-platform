package cn.zqkj.platform.system.domain.vo;

import java.time.LocalDateTime;

/**
 * 外部系统登记信息的API输出。
 *
 * @param id 主键
 * @param systemCode 稳定代码
 * @param systemName 名称
 * @param description 用途
 * @param enabled 是否启用
 * @param createdAt 创建UTC时间
 * @param updatedAt 修改UTC时间
 * @param version Base64并发版本
 */
public record ExternalSystemVO(long id, String systemCode, String systemName, String description, boolean enabled,
                               LocalDateTime createdAt, LocalDateTime updatedAt, String version) {
}
