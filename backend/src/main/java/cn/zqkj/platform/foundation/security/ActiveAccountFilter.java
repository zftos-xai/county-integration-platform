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
                    .map(account -> account.enabled() && account.loginName().equals(principal.getUsername()))
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
}
