package cn.zqkj.platform.system.bootstrap.domain.dto;

import cn.zqkj.platform.framework.security.validation.PasswordSize;

import cn.zqkj.platform.common.utils.Func;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * 一次性平台安全引导输入。
 *
 * <p>写入目标：{@code dbo.sys_user}。业务说明：创建首个本地管理员，密码只以哈希持久化；机构资料用于建立 {@code dbo.org_organization} 中的首个机构，此输入不是任一表的完整行。</p>
 *
 * @param organizationCode 首个机构代码
 * @param organizationName 首个机构名称
 * @param organizationType 经确认的机构类型代码
 * @param loginName 初始管理员登录名
 * @param displayName 初始管理员显示名称
 * @param initialPassword 初始管理员一次性密码
 */
public record BootstrapCommand(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+") String organizationCode,
        @NotBlank @Size(max = 200) String organizationName,
        @NotBlank @Size(max = 32) String organizationType,
        @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "[A-Za-z0-9._-]{3,64}") String loginName,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @PasswordSize String initialPassword
) {
    /**
     * 统一输入文本，密码等敏感原值不作裁剪。
     *
     * @param organizationCode 首个机构代码
     * @param organizationName 首个机构名称
     * @param organizationType 经确认的机构类型代码
     * @param loginName 初始管理员登录名
     * @param displayName 初始管理员显示名称
     * @param initialPassword 初始管理员一次性密码
     */
    public BootstrapCommand {
        organizationCode = Func.trimToNull(organizationCode);
        organizationName = Func.trimToNull(organizationName);
        organizationType = Func.trimToNull(organizationType);
        loginName = Func.trimToNull(loginName);
        if (loginName != null) {
            loginName = loginName.toLowerCase(Locale.ROOT);
        }
        displayName = Func.trimToNull(displayName);
    }
}
