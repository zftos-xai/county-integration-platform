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
    private final long userId;
    private final String username;
    private final String displayName;
    private transient String passwordHash;
    private final long primaryOrganizationId;
    private final String organizationCode;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final List<GrantedAuthority> authorities;

    /**
     * 创建创建后不能修改的平台登录用户信息。
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

    /** @return 用户内部主键 */
    public long userId() {
        return userId;
    }

    /** @return 管理页面显示名称 */
    public String displayName() {
        return displayName;
    }

    /** @return 主归属机构主键 */
    public long primaryOrganizationId() {
        return primaryOrganizationId;
    }

    /** @return 主归属机构代码 */
    public String organizationCode() {
        return organizationCode;
    }

    /** @return 是否必须先修改密码 */
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

    /** {@inheritDoc} */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public String getUsername() {
        return username;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
