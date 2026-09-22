package cn.zqkj.platform.framework.security.filter;

import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.common.utils.Func;
import java.util.Objects;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 逐请求复核账号版本、启用状态与权限，撤销改密、停用或授权变化前的旧会话。
 */
public class ActiveAccountFilter extends OncePerRequestFilter {

    private final IdentityMapper mapper;

    /**
     * 创建账号状态复核过滤器。
     *
     * @param mapper 身份持久化边界
     */
    public ActiveAccountFilter(IdentityMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 每次请求重新确认账号、权限和机构范围，变化后立即使旧会话失效。
     *
     * <p>使用用户行版本而非密码哈希判断账号变更；改密、重置和账号资料更新均撤销旧会话。
     * 角色与机构授权另行比较，避免仅修改关联表时漏掉权限撤销。</p>
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PlatformUserPrincipal principal) {
            boolean active = Optional.ofNullable(mapper.findById(principal.userId()))
                    .map(account -> account.enabled()
                            && Objects.equals(Func.encodeBase64(account.version()), principal.accountVersion())
                            && account.loginName().equals(principal.getUsername())
                            && account.primaryOrganizationId() == principal.primaryOrganizationId()
                            && account.organizationCode().equals(principal.organizationCode())
                            && authoritiesStillMatch(principal, account.mustChangePassword()))
                    .orElse(false);
            if (!active) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.sendError(HttpStatus.UNAUTHORIZED.value());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 比较会话权限与数据库当前权限、机构范围和强制改密状态。
     *
     * @param principal 会话中的登录用户
     * @param mustChangePassword 数据库当前强制改密状态
     * @return 完全一致时为true
     */
    private boolean authoritiesStillMatch(PlatformUserPrincipal principal, boolean mustChangePassword) {
        Set<String> expected = mustChangePassword
                ? Set.of("password:change")
                : Stream.concat(
                                mapper.findPermissionCodes(principal.userId()).stream(),
                                mapper.findOrganizationCodes(principal.userId()).stream().map(code -> "ORG:" + code)
                        )
                        .collect(Collectors.toUnmodifiableSet());
        Set<String> actual = principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
        return expected.equals(actual);
    }
}
