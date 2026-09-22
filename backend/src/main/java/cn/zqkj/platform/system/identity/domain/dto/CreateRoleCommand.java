package cn.zqkj.platform.system.identity.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * 创建平台角色输入。
 *
 * <p>写入目标：{@code dbo.sys_role}。业务说明：定义可分配给用户的业务角色；权限关系另行维护，此输入不是完整表行。</p>
 *
 * @param roleCode 稳定角色代码
 * @param roleName 角色名称
 */
public record CreateRoleCommand(
        @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "[A-Z][A-Z0-9_]{2,63}") String roleCode,
        @NotBlank @Size(max = 100) String roleName
) {
    /**
     * 统一业务标识和名称的首尾空白。
     *
     * @param roleCode 稳定角色代码
     * @param roleName 角色名称
     */
    public CreateRoleCommand {
        roleCode = Func.trimToNull(roleCode);
        if (roleCode != null) {
            roleCode = roleCode.toUpperCase(Locale.ROOT);
        }
        roleName = Func.trimToNull(roleName);
    }
}
