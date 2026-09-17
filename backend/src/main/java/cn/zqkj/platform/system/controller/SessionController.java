package cn.zqkj.platform.system.controller;

import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.system.domain.dto.ChangePasswordRequest;
import cn.zqkj.platform.system.domain.dto.LoginRequest;
import cn.zqkj.platform.system.domain.vo.CsrfTokenVO;
import cn.zqkj.platform.system.domain.vo.CurrentUserVO;
import cn.zqkj.platform.system.service.IdentityService;
import cn.zqkj.platform.system.service.ManagementAuditService;
import cn.zqkj.platform.system.service.impl.ManagementAuditServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供平台本地登录、登出、CSRF、当前用户和修改密码入口。
 */
@RestController
@RequestMapping("/api/v1/session")
public class SessionController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final IdentityService identityService;
    private final ManagementAuditService auditService;
    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    /**
     * 创建平台会话控制器。
     *
     * @param authenticationManager 认证管理器
     * @param contextRepository 会话安全上下文仓储
     * @param identityService 身份应用服务
     * @param auditService 管理审计服务
     */
    public SessionController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository contextRepository,
            IdentityService identityService,
            ManagementAuditService auditService
    ) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.identityService = identityService;
        this.auditService = auditService;
    }

    /**
     * 取得写请求所需CSRF令牌。
     *
     * @param csrfToken Spring Security生成的当前令牌
     * @return CSRF令牌元数据
     */
    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenVO> csrf(CsrfToken csrfToken) {
        return ApiResponse.success(new CsrfTokenVO(
                csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken()
        ));
    }

    /**
     * 校验本地账号并建立服务端会话。
     *
     * @param request 登录请求
     * @param servletRequest HTTP请求
     * @param servletResponse HTTP响应
     * @return 当前认证主体
     */
    @PostMapping("/login")
    public ApiResponse<CurrentUserVO> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.loginName(), request.password())
            );
        } catch (AuthenticationException exception) {
            auditService.recordLoginFailure(request.loginName(), ManagementAuditServiceImpl.currentRequestId());
            throw exception;
        }
        servletRequest.getSession(true);
        servletRequest.changeSessionId();
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        contextRepository.saveContext(context, servletRequest, servletResponse);
        return ApiResponse.success(CurrentUserVO.from((PlatformUserPrincipal) authentication.getPrincipal()));
    }

    /**
     * 查询当前会话主体。
     *
     * @param principal 当前认证主体
     * @return 当前用户视图
     */
    @GetMapping("/current")
    public ApiResponse<CurrentUserVO> current(@AuthenticationPrincipal PlatformUserPrincipal principal) {
        return ApiResponse.success(CurrentUserVO.from(principal));
    }

    /**
     * 注销当前服务端会话。
     *
     * @param authentication 当前认证信息
     * @param request HTTP请求
     * @param response HTTP响应
     * @return 空成功响应
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        logoutHandler.logout(request, response, authentication);
        return ApiResponse.success(null);
    }

    /**
     * 修改当前用户密码并注销当前会话，要求重新登录取得完整权限。
     *
     * @param request 密码修改请求
     * @param principal 当前认证主体
     * @param servletRequest HTTP请求
     * @param servletResponse HTTP响应
     * @return 空成功响应
     */
    @PostMapping("/password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        identityService.changePassword(principal.userId(), request.currentPassword(), request.newPassword());
        logoutHandler.logout(
                servletRequest,
                servletResponse,
                contextHolderStrategy.getContext().getAuthentication()
        );
        return ApiResponse.success(null);
    }
}
