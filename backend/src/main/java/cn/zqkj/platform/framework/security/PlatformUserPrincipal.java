package cn.zqkj.platform.framework.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 表示保存于服务端会话中的平台已登录用户。
 */
public final class PlatformUserPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;
    /** 平台用户内部主键。 */
    private final long userId;
    /** 已规范化的本地登录名。 */
    private final String username;
    /** 管理端显示名称。 */
    private final String displayName;
    /** 仅供Spring Security验证使用、可主动清除的BCrypt密码哈希。 */
    private transient String passwordHash;
    /** 主归属机构主键。 */
    private final long primaryOrganizationId;
    /** 主归属机构代码。 */
    private final String organizationCode;
    /** 用户和主归属机构是否均可用。 */
    private final boolean enabled;
    /** 当前会话是否只允许执行强制改密流程。 */
    private final boolean mustChangePassword;
    /** 由已启用角色解析出的功能权限。 */
    private final List<GrantedAuthority> authorities;

    /**
     * 创建后不可变的平台登录用户信息。
     *
     * @param userId 用户主键
     * @param username 登录名
     * @param displayName 显示名称
     * @param passwordHash BCrypt密码哈希
     * @param primaryOrganizationId 主机构主键
     * @param organizationCode 主机构代码
     * @param enabled 用户是否可用
     * @param mustChangePassword 是否必须修改密码
     * @param authorities 当前有效功能权限
     */
    public PlatformUserPrincipal(
            long userId,
            String username,
            String displayName,
            String passwordHash,
            long primaryOrganizationId,
            String organizationCode,
            boolean enabled,
            boolean mustChangePassword,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.primaryOrganizationId = primaryOrganizationId;
        this.organizationCode = organizationCode;
        this.enabled = enabled;
        this.mustChangePassword = mustChangePassword;
        this.authorities = List.copyOf(authorities);
    }

    /**
     * 返回当前登录用户的内部主键。
     *
     * @return 用户内部主键
     */
    public long userId() {
        return userId;
    }

    /**
     * 返回面向业务人员的显示名称。
     *
     * @return 管理页面显示名称
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 返回当前用户主归属机构主键。
     *
     * @return 主归属机构主键
     */
    public long primaryOrganizationId() {
        return primaryOrganizationId;
    }

    /**
     * 返回当前用户主归属机构代码。
     *
     * @return 主归属机构代码
     */
    public String organizationCode() {
        return organizationCode;
    }

    /**
     * 判断当前会话是否只能先完成强制改密。
     *
     * @return 是否必须先修改密码
     */
    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    /**
     * 从服务端装载的机构权限标识解析当前机构范围代码。
     *
     * @return 调用方不能修改的机构代码集合
     */
    public Set<String> organizationCodes() {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ORG:"))
                .map(authority -> authority.substring("ORG:".length()))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 返回由平台权限代码转换得到的只读授权集合。 */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /** 返回仅供Spring Security校验使用的密码哈希，不是明文密码。 */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /**
     * 认证成功后从会话中的登录用户中清除密码哈希副本。
     */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    /** 返回作为认证唯一标识的登录名。 */
    @Override
    public String getUsername() {
        return username;
    }

    /** 返回账号是否仍允许认证；请求过滤器还会复查数据库中的实时状态。 */
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
