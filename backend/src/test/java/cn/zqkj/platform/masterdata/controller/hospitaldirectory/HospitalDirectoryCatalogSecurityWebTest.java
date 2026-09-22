package cn.zqkj.platform.masterdata.controller.hospitaldirectory;

import cn.zqkj.platform.common.exception.GlobalExceptionHandler;
import cn.zqkj.platform.framework.config.SecurityConfiguration;
import cn.zqkj.platform.framework.security.OrganizationAccessGuard;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectoryPageVO;
import cn.zqkj.platform.masterdata.service.hospitaldirectory.HospitalDirectoryCatalogService;
import cn.zqkj.platform.system.identity.domain.model.UserAccount;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证医院综合目录只读API仍由服务端执行功能权限检查。 */
@WebMvcTest(controllers = HospitalDirectoryCatalogController.class)
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class, OrganizationAccessGuard.class})
class HospitalDirectoryCatalogSecurityWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private HospitalDirectoryCatalogService service;
    @MockitoBean private IdentityMapper identityMapper;
    @MockitoBean private UserDetailsService userDetailsService;

    /**
     * 验证医院综合目录接口要求基础数据只读权限。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void requiresMasterDataReadPermission() throws Exception {
        prepareAccount(List.of("master-data:read"));
        when(service.findPage(any(), any())).thenReturn(
                new HospitalDirectoryPageVO(List.of(), List.of(), 0, 1, 20));
        mockMvc.perform(get("/api/v1/master-data/hospital-directory")
                        .with(user(principal(List.of("master-data:read")))))
                .andExpect(status().isOk());

        prepareAccount(List.of());
        mockMvc.perform(get("/api/v1/master-data/hospital-directory")
                        .with(user(principal(List.of()))))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证医院目录筛选项与默认分页仍按原有GET协议绑定。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void bindsAndValidatesPageRequest() throws Exception {
        prepareAccount(List.of("master-data:read"));
        when(service.findPage(any(), any())).thenReturn(
                new HospitalDirectoryPageVO(List.of(), List.of(), 0, 1, 20));
        mockMvc.perform(get("/api/v1/master-data/hospital-directory")
                        .param("organizationCode", " ORG001 ").param("page", "2")
                        .with(user(principal(List.of("master-data:read")))))
                .andExpect(status().isOk());
        ArgumentCaptor<HospitalDirectoryQuery> query = ArgumentCaptor.forClass(HospitalDirectoryQuery.class);
        verify(service).findPage(query.capture(), ArgumentMatchers.eq(List.of("ORG001")));
        assertEquals("ORG001", query.getValue().organizationCode());
        assertEquals(2, query.getValue().page());
        assertEquals(20, query.getValue().pageSize());
        mockMvc.perform(get("/api/v1/master-data/hospital-directory").param("pageSize", "101")
                        .with(user(principal(List.of("master-data:read")))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/master-data/hospital-directory")
                        .param("organizationCode", "ORG002")
                        .with(user(principal(List.of("master-data:read")))))
                .andExpect(status().isForbidden());
        verifyNoMoreInteractions(service);
    }

    /**
     * 准备服务端账号状态查询替身。
     *
     * @param permissions 当前权限
     */
    private void prepareAccount(List<String> permissions) {
        when(identityMapper.findById(1L)).thenReturn(
                new UserAccount(1L, "admin", "Administrator", "hash", 10L, "ORG001", true, false, new byte[8]));
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
                1L, "admin", "Administrator", null, 10L, "ORG001", true, false, authorities, new byte[8]);
    }
}
