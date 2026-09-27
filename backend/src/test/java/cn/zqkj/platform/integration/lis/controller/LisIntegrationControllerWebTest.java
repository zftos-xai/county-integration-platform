package cn.zqkj.platform.integration.lis.controller;

import cn.zqkj.platform.common.exception.GlobalExceptionHandler;
import cn.zqkj.platform.framework.config.IntegrationSecurityConfiguration;
import cn.zqkj.platform.framework.config.SecurityConfiguration;
import cn.zqkj.platform.integration.lis.domain.dto.LisItemPackageQueryRequest;
import cn.zqkj.platform.integration.lis.domain.vo.LisItemPackageQueryResultVO;
import cn.zqkj.platform.integration.lis.exception.LisIntegrationOperationException;
import cn.zqkj.platform.integration.lis.service.LisIntegrationService;
import cn.zqkj.platform.system.configuration.domain.model.ExternalSystemCaller;
import cn.zqkj.platform.system.configuration.service.ExternalSystemInboundKeyAuthenticator;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证LIS机器入口真实HTTP映射、ID/Key认证和入站字段校验。 */
@WebMvcTest(controllers = LisIntegrationController.class)
@Import({SecurityConfiguration.class, IntegrationSecurityConfiguration.class, GlobalExceptionHandler.class})
class LisIntegrationControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LisIntegrationService service;

    @MockitoBean
    private ExternalSystemInboundKeyAuthenticator inboundKeyAuthenticator;

    @MockitoBean
    private IdentityMapper identityMapper;

    @MockitoBean
    private UserDetailsService userDetailsService;

    /** 验证Key识别身份后，控制器使用认证上下文身份编排请求。 */
    @Test
    void authenticatesCallerAndAcceptsValidReadRequest() throws Exception {
        ExternalSystemCaller caller = new ExternalSystemCaller("COUNTY_HOSPITAL", "县医院");
        when(inboundKeyAuthenticator.authenticate("COUNTY_HOSPITAL", "synthetic-key"))
                .thenReturn(Optional.of(caller));
        when(service.queryItemPackages(org.mockito.ArgumentMatchers.eq(caller),
                org.mockito.ArgumentMatchers.eq(new LisItemPackageQueryRequest("ORG-TEST")), anyString()))
                .thenAnswer(invocation -> new LisItemPackageQueryResultVO(invocation.getArgument(2),
                        "exchange-1", "SUCCESS", "1", "查询成功", List.of()));

        mockMvc.perform(post("/api/integration/v1/lis/item-packages/query")
                        .header("X-External-System-Id", "COUNTY_HOSPITAL")
                        .header("X-External-System-Key", "synthetic-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationCode\":\"ORG-TEST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationId").isString())
                .andExpect(jsonPath("$.data.exchangeRequestId").value("exchange-1"))
                .andExpect(jsonPath("$.data.outcome").value("SUCCESS"));

        verify(service).queryItemPackages(org.mockito.ArgumentMatchers.eq(caller),
                org.mockito.ArgumentMatchers.eq(new LisItemPackageQueryRequest("ORG-TEST")),
                anyString());
    }

    /** 验证错误Key无法触发LIS业务编排。 */
    @Test
    void rejectsInvalidKeyBeforeLisService() throws Exception {
        when(inboundKeyAuthenticator.authenticate("COUNTY_HOSPITAL", "bad-key"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/integration/v1/lis/item-packages/query")
                        .header("X-External-System-Id", "COUNTY_HOSPITAL")
                        .header("X-External-System-Key", "bad-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationCode\":\"ORG-TEST\"}"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).queryItemPackages(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    /** 验证格式错误的平台机构代码在进入业务编排前拒绝。 */
    @Test
    void rejectsInvalidOrganizationCodeAtHttpBoundary() throws Exception {
        ExternalSystemCaller caller = new ExternalSystemCaller("COUNTY_HOSPITAL", "县医院");
        when(inboundKeyAuthenticator.authenticate("COUNTY_HOSPITAL", "synthetic-key"))
                .thenReturn(Optional.of(caller));

        mockMvc.perform(post("/api/integration/v1/lis/item-packages/query")
                        .header("X-External-System-Id", "COUNTY_HOSPITAL")
                        .header("X-External-System-Key", "synthetic-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationCode\":\"错误代码\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).queryItemPackages(org.mockito.ArgumentMatchers.eq(caller),
                org.mockito.ArgumentMatchers.eq(new LisItemPackageQueryRequest("错误代码")), anyString());
    }

    /** 验证HIS结果未知通过502返回可定位编号，避免调用方把未知当作成功。 */
    @Test
    void returnsBadGatewayForUnknownTargetOutcome() throws Exception {
        ExternalSystemCaller caller = new ExternalSystemCaller("COUNTY_HOSPITAL", "县医院");
        when(inboundKeyAuthenticator.authenticate("COUNTY_HOSPITAL", "synthetic-key"))
                .thenReturn(Optional.of(caller));
        when(service.queryItemPackages(org.mockito.ArgumentMatchers.eq(caller),
                org.mockito.ArgumentMatchers.eq(new LisItemPackageQueryRequest("ORG-TEST")), anyString()))
                .thenReturn(new LisItemPackageQueryResultVO("operation-2", "exchange-2", "NO_RESPONSE",
                        null, "基层HIS调用结果无法确认，请按交换编号核实", List.of()));

        mockMvc.perform(post("/api/integration/v1/lis/item-packages/query")
                        .header("X-External-System-Id", "COUNTY_HOSPITAL")
                        .header("X-External-System-Key", "synthetic-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationCode\":\"ORG-TEST\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.data.outcome").value("NO_RESPONSE"))
                .andExpect(jsonPath("$.data.exchangeRequestId").value("exchange-2"));
    }

    /** 验证机构路由错误使用集成专属错误体并保留平台操作编号。 */
    @Test
    void returnsOperationIdWhenOrganizationCannotBeRouted() throws Exception {
        ExternalSystemCaller caller = new ExternalSystemCaller("COUNTY_HOSPITAL", "县医院");
        when(inboundKeyAuthenticator.authenticate("COUNTY_HOSPITAL", "synthetic-key"))
                .thenReturn(Optional.of(caller));
        when(service.queryItemPackages(org.mockito.ArgumentMatchers.eq(caller),
                org.mockito.ArgumentMatchers.eq(new LisItemPackageQueryRequest("UNKNOWN")), anyString()))
                .thenAnswer(invocation -> {
                    String operationId = invocation.getArgument(2);
                    throw LisIntegrationOperationException.notFound(operationId);
                });

        mockMvc.perform(post("/api/integration/v1/lis/item-packages/query")
                        .header("X-External-System-Id", "COUNTY_HOSPITAL")
                        .header("X-External-System-Key", "synthetic-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationCode\":\"UNKNOWN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"))
                .andExpect(jsonPath("$.operationId").isString())
                .andExpect(jsonPath("$.exchangeRequestId").doesNotExist());
    }
}
