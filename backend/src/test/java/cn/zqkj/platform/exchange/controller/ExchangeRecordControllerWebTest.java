package cn.zqkj.platform.exchange.controller;

import cn.zqkj.platform.common.exception.GlobalExceptionHandler;
import cn.zqkj.platform.exchange.domain.dto.ExchangeRecordQuery;
import cn.zqkj.platform.exchange.service.ExchangeRecordQueryService;
import cn.zqkj.platform.framework.config.SecurityConfiguration;
import cn.zqkj.platform.framework.security.OrganizationAccessGuard;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.identity.domain.model.UserAccount;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证交换记录GET参数绑定、功能权限和机构范围校验。 */
@WebMvcTest(controllers = ExchangeRecordController.class)
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class, OrganizationAccessGuard.class})
class ExchangeRecordControllerWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ExchangeRecordQueryService service;
    @MockitoBean private IdentityMapper identityMapper;
    @MockitoBean private UserDetailsService userDetailsService;

    /**
     * 验证带偏移时间和默认条数传入服务，并拒绝越权机构及非法条数。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void validatesAndBindsAuthorizedSearch() throws Exception {
        when(identityMapper.findById(1L)).thenReturn(
                new UserAccount(1L, "admin", "Administrator", "hash", 10L, "ORG001", true, false, new byte[8]));
        when(identityMapper.findPermissionCodes(1L)).thenReturn(List.of("exchange:read"));
        when(identityMapper.findOrganizationCodes(1L)).thenReturn(List.of("ORG001"));
        when(service.findRecent(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/exchange-records")
                        .param("organizationCode", "ORG001")
                        .param("receivedFrom", "2026-09-21T08:00:00+08:00")
                        .with(user(principal())))
                .andExpect(status().isOk());
        ArgumentCaptor<ExchangeRecordQuery> query = ArgumentCaptor.forClass(ExchangeRecordQuery.class);
        verify(service).findRecent(query.capture());
        assertEquals(LocalDateTime.parse("2026-09-21T00:00:00"), query.getValue().receivedFromUtc());
        assertEquals(20, query.getValue().limit());

        mockMvc.perform(get("/api/v1/exchange-records").param("organizationCode", "ORG002")
                        .with(user(principal())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/exchange-records")
                        .param("organizationCode", "ORG001").param("limit", "101")
                        .with(user(principal())))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证机构范围有效但缺少交换记录查询权限时，查询服务不会被调用。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void rejectsMissingExchangeReadPermission() throws Exception {
        when(identityMapper.findById(1L)).thenReturn(
                new UserAccount(1L, "admin", "Administrator", "hash", 10L, "ORG001", true, false, new byte[8]));
        when(identityMapper.findPermissionCodes(1L)).thenReturn(List.of());
        when(identityMapper.findOrganizationCodes(1L)).thenReturn(List.of("ORG001"));

        mockMvc.perform(get("/api/v1/exchange-records")
                        .param("organizationCode", "ORG001")
                        .with(user(principalWithoutExchangePermission())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    /**
     * 创建持有ORG001机构范围和交换记录查询权限的测试登录主体。
     *
     * @return 同时具备功能权限和单机构范围的用户
     */
    private PlatformUserPrincipal principal() {
        return new PlatformUserPrincipal(1L, "admin", "Administrator", null, 10L, "ORG001", true, false,
                List.of(new SimpleGrantedAuthority("ORG:ORG001"),
                        new SimpleGrantedAuthority("exchange:read")), new byte[8]);
    }

    /**
     * 创建仅持有ORG001机构范围的测试登录主体。
     *
     * @return 不具有交换记录查询功能权限的用户
     */
    private PlatformUserPrincipal principalWithoutExchangePermission() {
        return new PlatformUserPrincipal(1L, "admin", "Administrator", null, 10L, "ORG001", true, false,
                List.of(new SimpleGrantedAuthority("ORG:ORG001")), new byte[8]);
    }
}
