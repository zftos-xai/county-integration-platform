package cn.zqkj.platform.system.identity.service.impl;

import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.identity.domain.model.UserAccount;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import cn.zqkj.platform.system.identity.service.PlatformUserDetailsService;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 从平台身份库加载Spring Security登录用户。
 */
@Service
public class PlatformUserDetailsServiceImpl implements PlatformUserDetailsService {

    private final IdentityMapper mapper;

    /**
     * 创建平台用户查询服务。
     *
     * @param mapper 身份持久化边界
     */
    public PlatformUserDetailsServiceImpl(IdentityMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 加载本地账号、角色权限和机构范围，构造登录认证主体。
     *
     * <p>从本地账号、角色和机构范围组装认证主体；不存在的账号统一映射为认证失败，避免泄露账号状态。</p>
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Platform user was not found");
        }
        UserAccount account = Optional.ofNullable(
                        mapper.findByLoginName(username.trim().toLowerCase(Locale.ROOT)))
                .orElseThrow(() -> new UsernameNotFoundException("Platform user was not found"));
        List<SimpleGrantedAuthority> authorities;
        if (account.mustChangePassword()) {
            authorities = List.of(new SimpleGrantedAuthority("password:change"));
        } else {
            authorities = Stream.concat(
                            mapper.findPermissionCodes(account.id()).stream(),
                            mapper.findOrganizationCodes(account.id()).stream().map(code -> "ORG:" + code)
                    )
                    .distinct()
                    .sorted()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
        }
        return new PlatformUserPrincipal(
                account.id(), account.loginName(), account.displayName(), account.passwordHash(),
                account.primaryOrganizationId(), account.organizationCode(), account.enabled(),
                account.mustChangePassword(), authorities, account.version()
        );
    }
}
