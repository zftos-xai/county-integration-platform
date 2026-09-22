package cn.zqkj.platform.system.identity.controller;

import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.framework.config.SecurityConfiguration;
import cn.zqkj.platform.common.exception.GlobalExceptionHandler;
import cn.zqkj.platform.system.bootstrap.domain.vo.BootstrapResultVO;
import cn.zqkj.platform.system.bootstrap.domain.vo.BootstrapStatusVO;
import cn.zqkj.platform.system.bootstrap.controller.BootstrapController;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import cn.zqkj.platform.system.identity.service.IdentityService;
import cn.zqkj.platform.system.identity.domain.vo.CurrentUserVO;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证身份入口的匿名范围、CSRF强制检查和未登录默认拒绝行为。
 */
@WebMvcTest(controllers = {BootstrapController.class, SessionController.class})
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class})
class IdentitySecurityWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityService identityService;

    @MockitoBean
    private ManagementAuditService managementAuditService;

    @MockitoBean
    private IdentityMapper identityMapper;

    @MockitoBean
    private UserDetailsService userDetailsService;

    /**
     * 验证匿名调用只能读取不含启动密钥的安全引导状态。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void allowsAnonymousBootstrapStatus() throws Exception {
        when(identityService.getBootstrapStatus()).thenReturn(new BootstrapStatusVO(false, true));

        mockMvc.perform(get("/api/v1/bootstrap/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.initialized").value(false))
                .andExpect(jsonPath("$.data.bootstrapAvailable").value(true));
    }

    /**
     * 验证安全引导写请求缺少CSRF令牌时在进入应用服务前被拒绝。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void rejectsBootstrapWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/bootstrap")
                        .header("X-Platform-Bootstrap-Secret", "0123456789abcdef0123456789abcdef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bootstrapJson()))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证携带CSRF令牌和启动密钥的合法请求可进入一次性引导服务。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void acceptsBootstrapWithCsrf() throws Exception {
        when(identityService.bootstrap(eq("0123456789abcdef0123456789abcdef"), any()))
                .thenReturn(new BootstrapResultVO(30L, "admin", 10L, "ORG001", true));

        mockMvc.perform(post("/api/v1/bootstrap")
                        .with(csrf())
                        .header("X-Platform-Bootstrap-Secret", "0123456789abcdef0123456789abcdef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bootstrapJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(30L))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    /**
     * 验证未登录用户不能读取当前用户。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void rejectsAnonymousCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/session/current"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 验证合法登录建立服务端会话且后续请求可读取同一当前用户。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void establishesServerSideSession() throws Exception {
        String passwordHash = new BCryptPasswordEncoder(12).encode("Initial!Pass123");
        PlatformUserPrincipal principal = new PlatformUserPrincipal(
                30L, "admin", "Administrator", passwordHash, 10L, "ORG001", true, true,
                List.of(new SimpleGrantedAuthority("password:change")), new byte[8]);
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(principal);
        when(identityService.currentUser(any(PlatformUserPrincipal.class))).thenAnswer(invocation ->
                CurrentUserVO.from(invocation.getArgument(0), "County Hospital", List.of("平台管理员")));
        when(identityMapper.findById(30L)).thenReturn(
                new cn.zqkj.platform.system.identity.domain.model.UserAccount(
                        30L, "admin", "Administrator", passwordHash, 10L, "ORG001", true, true, new byte[8]
                )
        );

        MvcResult login = mockMvc.perform(post("/api/v1/session/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"admin\",\"password\":\"Initial!Pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginName").value("admin"))
                .andExpect(jsonPath("$.data.organizationName").value("County Hospital"))
                .andExpect(jsonPath("$.data.roleNames[0]").value("平台管理员"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/session/current").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").value("password:change"));

        byte[] changedVersion = new byte[8];
        changedVersion[7] = 1;
        when(identityMapper.findById(30L)).thenReturn(
                new cn.zqkj.platform.system.identity.domain.model.UserAccount(
                        30L, "admin", "Administrator", "changed-hash", 10L, "ORG001", true, true, changedVersion));
        mockMvc.perform(get("/api/v1/session/current").session(session))
                .andExpect(status().isUnauthorized());
        assertTrue(session.isInvalid());
    }

    /**
     * 验证登出请求销毁服务端会话，原会话不能继续重放。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void invalidatesSessionOnLogout() throws Exception {
        String passwordHash = new BCryptPasswordEncoder(12).encode("Initial!Pass123");
        PlatformUserPrincipal principal = new PlatformUserPrincipal(
                30L, "admin", "Administrator", passwordHash, 10L, "ORG001", true, true,
                List.of(new SimpleGrantedAuthority("password:change")), new byte[8]);
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(principal);
        when(identityService.currentUser(any(PlatformUserPrincipal.class))).thenAnswer(invocation ->
                CurrentUserVO.from(invocation.getArgument(0), "County Hospital", List.of("平台管理员")));
        when(identityMapper.findById(30L)).thenReturn(
                new cn.zqkj.platform.system.identity.domain.model.UserAccount(
                        30L, "admin", "Administrator", passwordHash, 10L, "ORG001", true, true, new byte[8]
                )
        );
        MvcResult login = mockMvc.perform(post("/api/v1/session/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"admin\",\"password\":\"Initial!Pass123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(post("/api/v1/session/logout").with(csrf()).session(session))
                .andExpect(status().isOk());

        assertTrue(session.isInvalid());
    }

    /**
     * 构造不含真实凭证的安全引导测试JSON。
     *
     * @return 测试请求JSON
     */
    private String bootstrapJson() {
        return """
                {
                  "organizationCode": "ORG001",
                  "organizationName": "County Hospital",
                  "organizationType": "HOSPITAL",
                  "loginName": "admin",
                  "displayName": "Administrator",
                  "initialPassword": "Initial!Pass123"
                }
                """;
    }
}
