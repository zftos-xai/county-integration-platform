package cn.zqkj.platform.system.identity.domain.dto;

import cn.zqkj.platform.framework.security.validation.PasswordSize;

import cn.zqkj.platform.common.utils.Func;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * 创建平台用户输入。
 *
 * <p>写入目标：{@code dbo.sys_user}。业务说明：建立本地登录账号；临时密码由服务编码为哈希后入库，此输入不是完整表行。</p>
 *
 * @param loginName 登录名
 * @param displayName 显示名称
 * @param primaryOrganizationId 主机构主键
 * @param temporaryPassword 一次性临时密码
 */
public record CreateUserCommand(
        @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "[A-Za-z0-9._-]{3,64}") String loginName,
        @NotBlank @Size(max = 100) String displayName,
        @Positive long primaryOrganizationId,
        @NotBlank @PasswordSize String temporaryPassword
) {
    /**
     * 统一输入文本，密码等敏感原值不作裁剪。
     *
     * @param loginName 登录名
     * @param displayName 显示名称
     * @param primaryOrganizationId 主机构主键
     * @param temporaryPassword 一次性临时密码
     */
    public CreateUserCommand {
        loginName = Func.trimToNull(loginName);
        if (loginName != null) {
            loginName = loginName.toLowerCase(Locale.ROOT);
        }
        displayName = Func.trimToNull(displayName);
    }
}
