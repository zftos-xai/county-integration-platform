package cn.zqkj.platform.foundation.security;

import cn.zqkj.platform.system.identity.application.IdentityRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 在每个已认证请求上复核用户和主机构仍处于启用状态。
 */
public class ActiveAccountFilter extends OncePerRequestFilter {

    private final IdentityRepository repository;

    /**
     * 创建账号状态复核过滤器。
     *
     * @param repository 身份持久化边界
     */
    public ActiveAccountFilter(IdentityRepository repository) {
        this.repository = repository;
    }

    /** {@inheritDoc} */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PlatformUserPrincipal principal) {
            boolean active = repository.findById(principal.userId())
                    .map(account -> account.enabled()
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
     * @param principal 会话主体
     * @param mustChangePassword 数据库当前强制改密状态
     * @return 完全一致时为true
     */
    private boolean authoritiesStillMatch(PlatformUserPrincipal principal, boolean mustChangePassword) {
        Set<String> expected = mustChangePassword
                ? Set.of("password:change")
                : Stream.concat(
                                repository.findPermissionCodes(principal.userId()).stream(),
                                repository.findOrganizationCodes(principal.userId()).stream().map(code -> "ORG:" + code)
                        )
                        .collect(Collectors.toUnmodifiableSet());
        Set<String> actual = principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
        return expected.equals(actual);
    }
}
