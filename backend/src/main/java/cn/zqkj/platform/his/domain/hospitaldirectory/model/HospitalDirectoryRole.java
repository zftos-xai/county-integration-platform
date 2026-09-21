package cn.zqkj.platform.his.domain.hospitaldirectory.model;

/**
 * 基层HIS医生目录实际返回的角色信息。
 *
 * @param roleCode 角色编码
 * @param roleName 角色名称
 * @param createdAt HIS返回的创建时间原值
 * @param updatedAt HIS返回的更新时间原值
 */
public record HospitalDirectoryRole(
        String roleCode,
        String roleName,
        String createdAt,
        String updatedAt
) {
}
