package cn.zqkj.platform.system.identity.domain.dto;

/**
 * 承载创建平台角色的已解析输入。
 *
 * @param roleCode 稳定角色代码
 * @param roleName 角色名称
 */
public record CreateRoleCommand(String roleCode, String roleName) {
}
