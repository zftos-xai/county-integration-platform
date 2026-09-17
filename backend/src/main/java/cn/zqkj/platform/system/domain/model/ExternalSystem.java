package cn.zqkj.platform.system.domain.model;

import java.time.LocalDateTime;

/**
 * 已确认外部系统快照。
 *
 * @param id 主键
 * @param systemCode 稳定系统代码
 * @param systemName 展示名称
 * @param description 用途说明
 * @param enabled 是否启用
 * @param createdAt 创建UTC时间
 * @param updatedAt 修改UTC时间
 * @param version SQL Server并发版本
 */
public record ExternalSystem(long id, String systemCode, String systemName, String description, boolean enabled,
                             LocalDateTime createdAt, LocalDateTime updatedAt, byte[] version) {
}
