package cn.zqkj.platform.system.configuration.domain.model;

import java.time.LocalDateTime;

/**
 * 已登记外部系统的只读快照。
 *
 * <p>对应表：{@code dbo.sys_external_system}（外部系统表）。</p>
 *
 * <p>业务说明：保存系统稳定代码和展示资料；具体环境、机构地址和凭证由端点表单独管理。</p>
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
