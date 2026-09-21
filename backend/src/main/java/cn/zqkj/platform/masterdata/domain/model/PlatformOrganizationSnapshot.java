package cn.zqkj.platform.masterdata.domain.model;

/**
 * 表示机构映射业务使用的平台机构最小快照。
 *
 * @param id 平台机构主键
 * @param code 平台机构代码
 * @param name 平台机构名称
 */
public record PlatformOrganizationSnapshot(long id, String code, String name) {
}
