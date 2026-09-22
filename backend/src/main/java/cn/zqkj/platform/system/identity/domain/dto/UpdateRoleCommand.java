package cn.zqkj.platform.system.identity.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改非系统角色输入。
 *
 * <p>写入目标：{@code dbo.sys_role}。业务说明：维护角色名称、说明和启用状态；稳定角色代码不变，此输入不是完整表行。</p>
 *
 * @param roleName 角色名称
 * @param enabled 是否启用
 * @param expectedVersion 客户端上次读取的八字节行版本；JSON使用Base64字段version
 */
public record UpdateRoleCommand(
        @NotBlank @Size(max = 100) String roleName,
        @NotNull Boolean enabled,
        @JsonProperty("version")
        @NotNull @Size(min = 8, max = 8) byte[] expectedVersion
) {
    /**
     * 统一业务标识和名称的首尾空白。
     *
     * @param roleName 角色名称
     * @param enabled 是否启用
     * @param expectedVersion 客户端上次读取的八字节行版本；JSON使用Base64字段version
     */
    public UpdateRoleCommand {
        roleName = Func.trimToNull(roleName);
        expectedVersion = expectedVersion == null ? null : expectedVersion.clone();
    }

    /** 返回本次修改所依据的行版本副本。
     * @return 八字节并发版本
     */
    @Override
    public byte[] expectedVersion() {
        return expectedVersion == null ? null : expectedVersion.clone();
    }
}
