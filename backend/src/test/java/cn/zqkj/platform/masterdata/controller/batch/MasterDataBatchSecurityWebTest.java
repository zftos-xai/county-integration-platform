package cn.zqkj.platform.masterdata.controller.batch;

import cn.zqkj.platform.common.exception.GlobalExceptionHandler;
import cn.zqkj.platform.framework.config.SecurityConfiguration;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchPageVO;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCounts;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataScopeType;
import cn.zqkj.platform.masterdata.service.batch.MasterDataBatchService;
import cn.zqkj.platform.masterdata.service.hospitaldirectory.HospitalDirectorySyncService;
import cn.zqkj.platform.masterdata.service.medicaldirectory.MedicalDirectorySyncService;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.identity.domain.model.UserAccount;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证基础数据同步批次查询只允许具有读取权限的有效用户。
 */
@WebMvcTest(controllers = MasterDataBatchController.class)
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class})
class MasterDataBatchSecurityWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MasterDataBatchService service;
    @MockitoBean private HospitalDirectorySyncService hospitalDirectorySyncService;
    @MockitoBean private MedicalDirectorySyncService medicalDirectorySyncService;
    @MockitoBean private IdentityMapper identityMapper;
    @MockitoBean private UserDetailsService userDetailsService;

    /**
     * 验证同步批次查询接口要求基础数据只读权限。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void enforcesMasterDataReadPermission() throws Exception {
        prepareAccount(List.of("master-data:read"));
        when(service.findPage(any(), any())).thenReturn(new MasterDataBatchPageVO(List.of(), 0, 1, 20));
        mockMvc.perform(get("/api/v1/master-data/batches").with(user(principal(List.of("master-data:read")))))
                .andExpect(status().isOk());

        prepareAccount(List.of());
        mockMvc.perform(get("/api/v1/master-data/batches").with(user(principal(List.of()))))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证分项结果读取接口与批次摘要使用相同的基础数据只读权限。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void protectsDirectoryResultReadWithMasterDataReadPermission() throws Exception {
        prepareAccount(List.of("master-data:read"));
        when(service.findHospitalDirectoryResults(any(Long.class), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/master-data/batches/25/directory-results")
                        .with(user(principal(List.of("master-data:read")))))
                .andExpect(status().isOk());

        prepareAccount(List.of());
        mockMvc.perform(get("/api/v1/master-data/batches/25/directory-results")
                        .with(user(principal(List.of()))))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证医院目录同步执行接口要求同步权限。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void protectsHospitalDirectoryRunWithSyncPermission() throws Exception {
        prepareAccount(List.of("master-data:sync"));
        when(service.get(any(Long.class), any())).thenReturn(hospitalBatch());
        mockMvc.perform(post("/api/v1/master-data/batches/25/run")
                        .with(user(principal(List.of("master-data:sync"))))
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"AQ==\"}"))
                .andExpect(status().isOk());

        prepareAccount(List.of("master-data:read"));
        mockMvc.perform(post("/api/v1/master-data/batches/25/run")
                        .with(user(principal(List.of("master-data:read"))))
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"AQ==\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证取消同步批次要求同步权限。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void protectsCancellationWithSyncPermission() throws Exception {
        prepareAccount(List.of("master-data:sync"));
        mockMvc.perform(post("/api/v1/master-data/batches/25/cancel")
                        .with(user(principal(List.of("master-data:sync"))))
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"AQ==\"}"))
                .andExpect(status().isOk());

        prepareAccount(List.of("master-data:read"));
        mockMvc.perform(post("/api/v1/master-data/batches/25/cancel")
                        .with(user(principal(List.of("master-data:read"))))
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"AQ==\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证同步选项接口要求同步权限。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void protectsSyncOptionsWithSyncPermission() throws Exception {
        prepareAccount(List.of("master-data:sync"));
        when(service.findSyncOptions(any())).thenReturn(
                new cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataSyncOptionsVO(List.of(), List.of()));
        mockMvc.perform(get("/api/v1/master-data/batches/options")
                        .with(user(principal(List.of("master-data:sync")))))
                .andExpect(status().isOk());

        prepareAccount(List.of("master-data:read"));
        mockMvc.perform(get("/api/v1/master-data/batches/options")
                        .with(user(principal(List.of("master-data:read")))))
                .andExpect(status().isForbidden());
    }

    /**
     * 准备服务端账号状态查询替身。
     *
     * @param permissions 当前权限
     */
    private void prepareAccount(List<String> permissions) {
        when(identityMapper.findById(1L)).thenReturn(
                new UserAccount(1L, "admin", "Administrator", "hash", 10L, "ORG001", true, false)
        );
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
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        permissions.forEach(value -> authorities.add(new SimpleGrantedAuthority(value)));
        authorities.add(new SimpleGrantedAuthority("ORG:ORG001"));
        return new PlatformUserPrincipal(
                1L, "admin", "Administrator", null, 10L, "ORG001", true, false, authorities
        );
    }

    /**
     * 创建执行权限测试需要的医院目录批次摘要。
     *
     * @return 可执行100-003批次摘要
     */
    private MasterDataBatchSummaryVO hospitalBatch() {
        return new MasterDataBatchSummaryVO(
                25L, "BD-TEST", MasterDataScopeType.ORGANIZATION, "ORG001", "测试机构",
                ParameterEnvironment.TEST, MasterDataCategory.HOSPITAL_DIRECTORY, "100-003", null,
                null, "HIS-ORG-001", null, null, MasterDataBatchStatus.CREATED,
                new MasterDataBatchCounts(null, 0, 0, 0, 0, 0, 0, 0, 0, null),
                null, null, null, null, null, "AQ==");
    }
}
