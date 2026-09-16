package cn.zqkj.platform.system.identity.application;

import cn.zqkj.platform.foundation.security.PlatformUserPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 从平台身份库加载Spring Security认证主体。
 */
@Service
public class PlatformUserDetailsService implements UserDetailsService {

    private final IdentityRepository repository;

    /**
     * 创建平台用户查询服务。
     *
     * @param repository 身份持久化边界
     */
    public PlatformUserDetailsService(IdentityRepository repository) {
        this.repository = repository;
    }

    /** {@inheritDoc} */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Platform user was not found");
        }
        UserAccount account = repository.findByLoginName(username.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Platform user was not found"));
        List<SimpleGrantedAuthority> authorities = account.mustChangePassword()
                ? List.of(new SimpleGrantedAuthority("password:change"))
                : repository.findPermissionCodes(account.id()).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
        return new PlatformUserPrincipal(
                account.id(), account.loginName(), account.displayName(), account.passwordHash(),
                account.primaryOrganizationId(), account.organizationCode(), account.enabled(),
                account.mustChangePassword(), authorities
        );
    }
}
