package cn.zqkj.platform.masterdata.controller.batch;

import cn.zqkj.platform.common.exception.GlobalExceptionHandler;
import cn.zqkj.platform.framework.config.SecurityConfiguration;
import cn.zqkj.platform.framework.security.OrganizationAccessGuard;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.masterdata.domain.batch.dto.MasterDataBatchQuery;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCounts;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchStatus;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataScopeType;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataSyncMode;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchPageVO;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataBatchSummaryVO;
import cn.zqkj.platform.masterdata.domain.batch.vo.MasterDataSyncOptionsVO;
import cn.zqkj.platform.masterdata.service.batch.MasterDataBatchService;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.identity.domain.model.UserAccount;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class, OrganizationAccessGuard.class})
class MasterDataBatchSecurityWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MasterDataBatchService service;
    @MockitoBean private IdentityMapper identityMapper;
    @MockitoBean private UserDetailsService userDetailsService;

    /** 中断恢复同样要求同步权限、CSRF 和合法版本，不能靠只读权限触发。 */
    @Test
    void protectsRecoveryAndRejectsInvalidVersionBeforeService() throws Exception {
        prepareAccount(List.of("master-data:read"));
        mockMvc.perform(post("/api/v1/master-data/batches/25/recover")
                        .with(user(principal(List.of("master-data:read")))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"AAAAAAAAAAA=\"}"))
                .andExpect(status().isForbidden());
        prepareAccount(List.of("master-data:sync"));
        mockMvc.perform(post("/api/v1/master-data/batches/25/recover")
                        .with(user(principal(List.of("master-data:sync")))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/master-data/batches/25/recover")
                        .with(user(principal(List.of("master-data:sync"))))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"AAAAAAAAAAA=\"}"))
                .andExpect(status().isForbidden());
        verifyNoMoreInteractions(service);
        mockMvc.perform(post("/api/v1/master-data/batches/25/recover")
                        .with(user(principal(List.of("master-data:sync")))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"AAAAAAAAAAA=\"}"))
                .andExpect(status().isOk());
        verify(service).recover(ArgumentMatchers.eq(25L), ArgumentMatchers.eq(new byte[8]), any());
    }

    /**
     * 类别与时间范围不匹配、非法枚举或畸形请求均在入口拒绝，不启动业务查询。
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void rejectsInvalidBatchInputsBeforeService() throws Exception {
        prepareAccount(List.of("master-data:sync"));
        String prefix = "{\"requestKey\":\"8BCDCA4E11A44A9D888D7E70\","
                + "\"organizationCode\":\"ORG001\",\"environment\":\"PRODUCTION\",";
        List<String> invalidBodies = List.of(
                prefix + "\"category\":\"MEDICAL_DIRECTORY\",\"mode\":\"TIME_RANGE\"}",
                prefix + "\"category\":\"MEDICAL_DIRECTORY\",\"mode\":\"TIME_RANGE\",\"rangeStart\":\"2026-09-21T08:00:00+08:00\"}",
                prefix + "\"category\":\"MEDICAL_DIRECTORY\",\"mode\":\"TIME_RANGE\",\"rangeStart\":\"2026-09-21T08:00:00+08:00\","
                        + "\"rangeEnd\":\"2026-09-20T08:00:00+08:00\"}",
                prefix + "\"category\":\"MEDICAL_DIRECTORY\",\"mode\":\"FULL\",\"rangeStart\":\"2026-09-21T08:00:00+08:00\"}",
                prefix + "\"category\":\"HOSPITAL_DIRECTORY\",\"mode\":\"NOT_APPLICABLE\",\"rangeEnd\":\"2026-09-21T08:00:00+08:00\"}",
                prefix + "\"category\":\"UNKNOWN\"}", "null", "{");
        for (String body : invalidBodies) {
            mockMvc.perform(post("/api/v1/master-data/batches")
                            .with(user(principal(List.of("master-data:sync")))).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoMoreInteractions(service);
    }

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
     * 验证批次查询的分页默认值与带偏移时间转换。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void bindsAndValidatesPageRequest() throws Exception {
        prepareAccount(List.of("master-data:read"));
        when(service.findPage(any(), any())).thenReturn(new MasterDataBatchPageVO(List.of(), 0, 1, 20));
        mockMvc.perform(get("/api/v1/master-data/batches")
                        .param("startedFrom", "2026-09-21T08:00:00+08:00")
                        .with(user(principal(List.of("master-data:read")))))
                .andExpect(status().isOk());
        ArgumentCaptor<MasterDataBatchQuery> query = ArgumentCaptor.forClass(MasterDataBatchQuery.class);
        verify(service).findPage(query.capture(), ArgumentMatchers.eq(List.of("ORG001")));
        assertEquals(LocalDateTime.parse("2026-09-21T00:00:00"), query.getValue().startedFromUtc());
        assertEquals(1, query.getValue().page());
        assertEquals(20, query.getValue().pageSize());
        mockMvc.perform(get("/api/v1/master-data/batches").param("page", "0")
                        .with(user(principal(List.of("master-data:read")))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/master-data/batches")
                        .param("organizationCode", "ORG002")
                        .with(user(principal(List.of("master-data:read")))))
                .andExpect(status().isForbidden());
        verifyNoMoreInteractions(service);
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
        when(service.run(any(Long.class), any(), any())).thenReturn(hospitalBatch());
        mockMvc.perform(post("/api/v1/master-data/batches/25/run")
                        .with(user(principal(List.of("master-data:sync"))))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"AAAAAAAAAAE=\"}"))
                .andExpect(status().isOk());

        prepareAccount(List.of("master-data:read"));
        mockMvc.perform(post("/api/v1/master-data/batches/25/run")
                        .with(user(principal(List.of("master-data:read"))))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"AAAAAAAAAAE=\"}"))
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"AAAAAAAAAAE=\"}"))
                .andExpect(status().isOk());

        prepareAccount(List.of("master-data:read"));
        mockMvc.perform(post("/api/v1/master-data/batches/25/cancel")
                        .with(user(principal(List.of("master-data:read"))))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"AAAAAAAAAAE=\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * 明确指定的机构必须在入口校验；越权请求不得进入创建批次用例。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void rejectsUnauthorizedBatchStartBeforeServiceCall() throws Exception {
        prepareAccount(List.of("master-data:sync"));

        mockMvc.perform(post("/api/v1/master-data/batches")
                        .with(user(principal(List.of("master-data:sync"))))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestKey\":\"8BCDCA4E11A44A9D888D7E70\","
                                + "\"organizationCode\":\"ORG002\",\"environment\":\"PRODUCTION\","
                                + "\"category\":\"HOSPITAL_DIRECTORY\",\"mode\":\"NOT_APPLICABLE\"}"))
                .andExpect(status().isForbidden());

        verifyNoMoreInteractions(service);
    }

    /**
     * Base64文本能解码但不是八字节行版本时必须在入口拒绝。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void rejectsNonRowVersionLength() throws Exception {
        prepareAccount(List.of("master-data:sync"));
        mockMvc.perform(post("/api/v1/master-data/batches/25/cancel")
                        .with(user(principal(List.of("master-data:sync"))))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"AQ==\"}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 执行请求的版本错误必须先于按 ID 读取批次被拒绝，避免无效请求触发数据库访问。
     *
     * @throws Exception MockMvc调用失败时抛出
     */
    @Test
    void rejectsInvalidRunVersionBeforeReadingBatch() throws Exception {
        prepareAccount(List.of("master-data:sync"));

        mockMvc.perform(post("/api/v1/master-data/batches/25/run")
                        .with(user(principal(List.of("master-data:sync"))))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"AQ==\"}"))
                .andExpect(status().isBadRequest());

        verifyNoMoreInteractions(service);
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
                new MasterDataSyncOptionsVO(List.of(), List.of()));
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
                new UserAccount(1L, "admin", "Administrator", "hash", 10L, "ORG001", true, false, new byte[8])
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
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        permissions.forEach(value -> authorities.add(new SimpleGrantedAuthority(value)));
        authorities.add(new SimpleGrantedAuthority("ORG:ORG001"));
        return new PlatformUserPrincipal(
                1L, "admin", "Administrator", null, 10L, "ORG001", true, false, authorities, new byte[8]);
    }

    /**
     * 创建执行权限测试需要的医院目录批次摘要。
     *
     * @return 可执行100-003批次摘要
     */
    private MasterDataBatchSummaryVO hospitalBatch() {
        return new MasterDataBatchSummaryVO(
                25L, "BD-TEST", MasterDataScopeType.ORGANIZATION, "ORG001", "测试机构",
                ParameterEnvironment.TEST, MasterDataCategory.HOSPITAL_DIRECTORY, MasterDataSyncMode.NOT_APPLICABLE,
                "100-003", null, null, "HIS-ORG-001", null, null, null, MasterDataBatchStatus.CREATED,
                new MasterDataBatchCounts(null, 0, 0, 0, 0, 0, 0, 0, 0, null),
                null, null, null, null, null, "AQ==");
    }
}
