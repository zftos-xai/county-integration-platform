package cn.zqkj.platform.exchange.controller;

import cn.zqkj.platform.common.exception.GlobalExceptionHandler;
import cn.zqkj.platform.exchange.service.PhisTradeCatalogService;
import cn.zqkj.platform.framework.config.SecurityConfiguration;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.identity.domain.model.UserAccount;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证全局交易目录API复用交换记录查看权限且不依赖机构范围。 */
@WebMvcTest(controllers = PhisTradeCatalogController.class)
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class})
class PhisTradeCatalogControllerWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PhisTradeCatalogService service;
    @MockitoBean private IdentityMapper identityMapper;
    @MockitoBean private UserDetailsService userDetailsService;

    /** 验证授权用户可读全局目录，无机构请求参数。 */
    @Test
    void returnsCatalogForExchangeReader() throws Exception {
        prepareAccount(List.of("exchange:read"));
        when(service.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/his/trades").with(user(principal(true))))
                .andExpect(status().isOk());

        verify(service).findAll();
    }

    /** 验证无exchange:read权限时目录服务不会执行。 */
    @Test
    void rejectsUsersWithoutExchangeReadPermission() throws Exception {
        prepareAccount(List.of());

        mockMvc.perform(get("/api/v1/his/trades").with(user(principal(false))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    private void prepareAccount(List<String> permissions) {
        when(identityMapper.findById(1L)).thenReturn(
                new UserAccount(1L, "admin", "Administrator", "hash", 10L, "ORG001", true, false, new byte[8]));
        when(identityMapper.findPermissionCodes(1L)).thenReturn(permissions);
        when(identityMapper.findOrganizationCodes(1L)).thenReturn(List.of());
    }

    private PlatformUserPrincipal principal(boolean canRead) {
        List<SimpleGrantedAuthority> authorities = canRead
                ? List.of(new SimpleGrantedAuthority("exchange:read"))
                : List.of();
        return new PlatformUserPrincipal(1L, "admin", "Administrator", null, 10L, "ORG001", true, false,
                authorities, new byte[8]);
    }
}
