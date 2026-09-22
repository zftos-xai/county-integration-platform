package cn.zqkj.platform.system.identity.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 修改平台用户基础资料输入。
 *
 * <p>写入目标：{@code dbo.sys_user}。业务说明：维护账号展示名称和主归属机构；不修改登录名、密码及启用状态，此输入不是完整表行。</p>
 *
 * @param displayName 显示名称
 * @param primaryOrganizationId 主机构主键
 * @param expectedVersion 客户端上次读取的八字节行版本；JSON使用Base64字段version
 */
public record UpdateUserCommand(
        @NotBlank @Size(max = 100) String displayName,
        @Positive long primaryOrganizationId,
        @JsonProperty("version")
        @NotNull @Size(min = 8, max = 8) byte[] expectedVersion
) {
    /**
     * 统一业务标识和名称的首尾空白。
     *
     * @param displayName 显示名称
     * @param primaryOrganizationId 主机构主键
     * @param expectedVersion 客户端上次读取的八字节行版本；JSON使用Base64字段version
     */
    public UpdateUserCommand {
        displayName = Func.trimToNull(displayName);
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
