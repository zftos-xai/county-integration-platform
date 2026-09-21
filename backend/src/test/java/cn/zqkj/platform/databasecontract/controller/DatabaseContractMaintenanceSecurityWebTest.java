package cn.zqkj.platform.databasecontract.controller;

import cn.zqkj.platform.common.exception.GlobalExceptionHandler;
import cn.zqkj.platform.databasecontract.domain.vo.DatabaseContractInspectionVO;
import cn.zqkj.platform.databasecontract.service.DatabaseContractMaintenanceService;
import cn.zqkj.platform.framework.config.SecurityConfiguration;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.identity.domain.model.UserAccount;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证数据库契约读取、制订、审批和执行权限彼此隔离。 */
@WebMvcTest(controllers = DatabaseContractMaintenanceController.class)
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class})
class DatabaseContractMaintenanceSecurityWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DatabaseContractMaintenanceService service;
    @MockitoBean private IdentityMapper identityMapper;
    @MockitoBean private UserDetailsService userDetailsService;

    /**
     * 验证数据库契约扫描接口要求只读权限。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void protectsInspectionWithReadPermission() throws Exception {
        prepareAccount(List.of("database-contract:read"));
        when(service.inspect()).thenReturn(new DatabaseContractInspectionVO(
                LocalDateTime.of(2026, 9, 19, 10, 0), 0, 0, 0, List.of()));
        mockMvc.perform(get("/api/v1/database-contract/inspection")
                        .with(user(principal(List.of("database-contract:read")))))
                .andExpect(status().isOk());

        prepareAccount(List.of());
        mockMvc.perform(get("/api/v1/database-contract/inspection")
                        .with(user(principal(List.of()))))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证数据库契约只读权限不能创建维护方案。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void readPermissionCannotCreatePlan() throws Exception {
        prepareAccount(List.of("database-contract:read"));
        mockMvc.perform(post("/api/v1/database-contract/plans")
                        .with(user(principal(List.of("database-contract:read"))))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"issueKeys\":[\"DBCONTRACT-E103::dbo.sample.name\"],\"summary\":\"扩大字段长度\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证方案创建权限不能越权审批或执行方案。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void planPermissionCannotApproveOrExecute() throws Exception {
        prepareAccount(List.of("database-contract:plan"));
        PlatformUserPrincipal principal = principal(List.of("database-contract:plan"));
        mockMvc.perform(post("/api/v1/database-contract/plans/25/approve")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"AAAAAAAAAAA=\",\"note\":\"已核对影响范围和执行窗口\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/database-contract/plans/25/execute")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"AAAAAAAAAAA=\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * 准备服务端账号状态查询替身。
     *
     * @param permissions 当前权限
     */
    private void prepareAccount(List<String> permissions) {
        when(identityMapper.findById(1L)).thenReturn(
                new UserAccount(1L, "admin", "Administrator", "hash", 10L, "ORG001", true, false));
        when(identityMapper.findPermissionCodes(1L)).thenReturn(permissions);
        when(identityMapper.findOrganizationCodes(1L)).thenReturn(List.of("ORG001"));
    }

    /**
     * 创建具有指定功能权限的测试登录主体。
     *
     * @param permissions 功能权限
     * @return 测试用户
     */
    private PlatformUserPrincipal principal(List<String> permissions) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        permissions.forEach(value -> authorities.add(new SimpleGrantedAuthority(value)));
        authorities.add(new SimpleGrantedAuthority("ORG:ORG001"));
        return new PlatformUserPrincipal(
                1L, "admin", "Administrator", null, 10L, "ORG001", true, false, authorities);
    }
}
