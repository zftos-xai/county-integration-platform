package cn.zqkj.platform.his.client;

import cn.zqkj.platform.his.domain.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.his.domain.dto.MedicalDirectoryCountQuery;
import cn.zqkj.platform.his.domain.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.his.domain.dto.OrganizationQuery;
import cn.zqkj.platform.his.domain.model.HospitalDirectoryEntry;
import cn.zqkj.platform.his.domain.model.HospitalDirectoryType;
import cn.zqkj.platform.his.domain.model.MedicalDirectoryEntry;
import cn.zqkj.platform.his.domain.model.MedicalDirectoryType;
import cn.zqkj.platform.his.domain.model.OrganizationEntry;
import cn.zqkj.platform.his.domain.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.his.exception.PhisRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证基层HIS客户端使用WSDL确认的HTTP、SOAPAction和受控异常边界。
 */
class SoapPhisClientTest {

    /** 验证100-005数量查询只接受单条非负整数，并发送与分页数据一致的范围条件。 */
    @Test
    void queriesMedicalDirectoryDeclaredCount() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(exchange, 200, soapResponse("{\"result\":\"1\",\"msg\":[{\"行数\":\"37\"}]}"));
        });

        try {
            PhisResponse<Long> response = client().countMedicalDirectory(
                    context(server),
                    new MedicalDirectoryCountQuery(
                            MedicalDirectoryType.WESTERN_MEDICINE, null,
                            LocalDateTime.of(2026, 9, 1, 0, 0),
                            LocalDateTime.of(2026, 9, 20, 23, 59, 59), "ORG-001"));

            assertTrue(response.success());
            assertEquals(37L, response.data());
            assertTrue(requestBody.get().contains("<TradeCode>100-005</TradeCode>"));
            assertTrue(requestBody.get().contains("开始时间"));
            assertTrue(requestBody.get().contains("2026-09-01 00:00:00"));
            assertTrue(requestBody.get().contains("机构编码"));
        } finally {
            server.stop(0);
        }
    }

    /** 验证100-004分页边界、扩展字段和来源启用值按强类型返回。 */
    @Test
    void queriesMedicalDirectoryPage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(exchange, 200, soapResponse("{\"result\":\"1\",\"msg\":[{"
                    + "\"目录编码\":\"M001\",\"目录名称\":\"测试药品\",\"目录类别名称\":\"西药\","
                    + "\"规格\":\"10mg\",\"包装单位\":\"盒\",\"转换系数\":10,\"是否启用\":true}]}"));
        });

        try {
            PhisResponse<List<MedicalDirectoryEntry>> response = client().queryMedicalDirectory(
                    context(server),
                    new MedicalDirectoryQuery(
                            MedicalDirectoryType.WESTERN_MEDICINE, null, 1, 100,
                            LocalDateTime.of(2026, 9, 1, 0, 0),
                            LocalDateTime.of(2026, 9, 20, 23, 59, 59), "ORG-001"));

            assertTrue(response.success());
            assertEquals("M001", response.data().get(0).directoryCode());
            assertEquals("10", response.data().get(0).conversionFactor());
            assertEquals("true", response.data().get(0).enabledFlag());
            assertTrue(requestBody.get().contains("<TradeCode>100-004</TradeCode>"));
            assertTrue(requestBody.get().contains("开始行数"));
            assertTrue(requestBody.get().contains("结束行数"));
        } finally {
            server.stop(0);
        }
    }

    /**
     * 验证客户端保留配置中的ASMX操作地址，但向WebService基础地址发送SOAP请求并解析结果。
     */
    @Test
    void postsSoapRequestToHttpServiceAddress() throws Exception {
        AtomicReference<String> soapAction = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> requestQuery = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            soapAction.set(exchange.getRequestHeaders().getFirst("SOAPAction"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requestQuery.set(exchange.getRequestURI().getRawQuery());
            send(exchange, 200, soapResponse(
                    "{\"result\":\"1\",\"msg\":[{\"目录编码\":\"D001\",\"目录名称\":\"内科\","
                            + "\"角色\":[{\"角色编码\":\"R01\",\"角色名称\":\"医生\"}]}]}"));
        });

        try {
            PhisProtocolClient client = client();
            URI serviceUri = URI.create(serviceUri(server) + "?op=PHIS_Interface");
            PhisResponse<List<HospitalDirectoryEntry>> response = client.queryHospitalDirectory(
                    PhisInvocationContext.create(serviceUri, 1_000, 3_000, "SYNTHETIC-AUTH"),
                    query()
            );

            assertTrue(response.success());
            assertEquals("D001", response.data().get(0).directoryCode());
            assertEquals("R01", response.data().get(0).roles().get(0).roleCode());
            assertEquals("医生", response.data().get(0).roles().get(0).roleName());
            assertEquals("\"http://tempuri.org/PHIS_Interface\"", soapAction.get());
            assertEquals(null, requestQuery.get());
            assertTrue(requestBody.get().contains("<TradeCode>100-003</TradeCode>"));
            assertTrue(requestBody.get().contains("<InputParameter>"));
            assertTrue(requestBody.get().contains("SYNTHETIC-AUTH"));
        } finally {
            server.stop(0);
        }
    }

    /**
     * 验证HTTP失败只返回状态码，不把目标响应正文带入异常。
     */
    @Test
    void rejectsHttpFailureWithoutResponseBody() throws Exception {
        HttpServer server = startServer(exchange -> send(exchange, 500, "sensitive-target-body"));

        try {
            PhisCommunicationException exception = assertThrows(
                    PhisCommunicationException.class,
                    () -> client().queryHospitalDirectory(
                            context(server),
                            query()
                    )
            );

            assertTrue(exception.getMessage().contains("500"));
            assertTrue(!exception.getMessage().contains("sensitive-target-body"));
        } finally {
            server.stop(0);
        }
    }

    /** 验证目标业务错误回显授权码时，客户端会在返回业务层前清除该凭证。 */
    @Test
    void redactsAuthorizationCodeEchoedByBusinessFailure() throws Exception {
        HttpServer server = startServer(exchange -> send(exchange, 200, soapResponse(
                "{\"result\":\"0\",\"msg\":\"授权失败，验证码：SYNTHETIC-AUTH\"}")));

        try {
            PhisResponse<List<HospitalDirectoryEntry>> response = client().queryHospitalDirectory(
                    context(server), query());

            assertTrue(!response.success());
            assertTrue(response.errorMessage().contains("***"));
            assertTrue(!response.errorMessage().contains("SYNTHETIC-AUTH"));
        } finally {
            server.stop(0);
        }
    }

    /** 验证调用上下文拒绝未登记的查询参数，且请求不会进入协议客户端。 */
    @Test
    void rejectsUnsupportedQueryParameter() {
        assertThrows(PhisRequestException.class, () -> PhisInvocationContext.create(
                URI.create("http://his.example.invalid/WebService.asmx?foo=bar"),
                1_000, 3_000, "SYNTHETIC-AUTH"));
    }

    /** 验证100-008只发送医院名称并转换来源机构字段。 */
    @Test
    void queriesOrganizations() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(exchange, 200, soapResponse("{\"result\":\"1\",\"msg\":[{"
                    + "\"ID\":\"H001\",\"医院名称\":\"县人民医院\",\"联系电话\":\"12345\"}]}"));
        });

        try {
            PhisResponse<List<OrganizationEntry>> response =
                    client().queryOrganizations(context(server), new OrganizationQuery("县人民医院"));

            assertTrue(response.success());
            assertEquals("H001", response.data().get(0).sourceOrganizationId());
            assertEquals("县人民医院", response.data().get(0).hospitalName());
            assertTrue(requestBody.get().contains("<TradeCode>100-008</TradeCode>"));
            assertTrue(requestBody.get().contains("医院名称"));
        } finally {
            server.stop(0);
        }
    }

    /** 验证四项运行配置作为整体固定脱敏，且空查询在发送前被拒绝。 */
    @Test
    void redactsInvocationContextAndRejectsInvalidQueryBeforeSend() {
        PhisInvocationContext context = PhisInvocationContext.create(
                URI.create("http://his.example.invalid/WebService.asmx"),
                1_000, 3_000, "SYNTHETIC-AUTH");

        assertEquals("PhisInvocationContext[REDACTED]", context.toString());
        assertTrue(!context.toString().contains("SYNTHETIC-AUTH"));
        assertThrows(PhisRequestException.class,
                () -> client().queryHospitalDirectory(context, null));
    }

    /** 验证调用上下文集中拒绝无效超时、空授权码和带账号的地址。 */
    @Test
    void validatesInvocationContextAsOneUnit() {
        URI endpoint = URI.create("http://his.example.invalid/WebService.asmx");

        assertThrows(PhisRequestException.class,
                () -> PhisInvocationContext.create(endpoint, 99, 3_000, "SYNTHETIC-AUTH"));
        assertThrows(PhisRequestException.class,
                () -> PhisInvocationContext.create(endpoint, 3_000, 2_999, "SYNTHETIC-AUTH"));
        assertThrows(PhisRequestException.class,
                () -> PhisInvocationContext.create(endpoint, 1_000, 3_000, " "));
        assertThrows(PhisRequestException.class,
                () -> PhisInvocationContext.create(
                        URI.create("http://user:password@his.example.invalid/WebService.asmx"),
                        1_000, 3_000, "SYNTHETIC-AUTH"));
    }

    /** 验证100-004单页超过协议上限时在网络请求前被拒绝。 */
    @Test
    void rejectsOversizedMedicalDirectoryPageBeforeSend() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);

        assertThrows(PhisRequestException.class, () -> client().queryMedicalDirectory(
                PhisInvocationContext.create(
                        URI.create("http://his.example.invalid/WebService.asmx"),
                        1_000, 3_000, "SYNTHETIC-AUTH"),
                new MedicalDirectoryQuery(
                        MedicalDirectoryType.WESTERN_MEDICINE, null, 1, 101,
                        start, start.plusDays(1), null)));
    }

    /** 验证100-005成功响应中的负数不被当作合法声明行数。 */
    @Test
    void rejectsNegativeDeclaredCount() throws Exception {
        HttpServer server = startServer(exchange -> send(exchange, 200, soapResponse(
                "{\"result\":\"1\",\"msg\":[{\"行数\":\"-1\"}]}")));

        try {
            LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
            assertThrows(PhisProtocolException.class, () -> client().countMedicalDirectory(
                    context(server),
                    new MedicalDirectoryCountQuery(
                            MedicalDirectoryType.WESTERN_MEDICINE, null,
                            start, start.plusDays(1), null)));
        } finally {
            server.stop(0);
        }
    }

    /**
     * 创建使用真实编解码器的客户端。
     *
     * @return 基层HIS客户端
     */
    private PhisProtocolClient client() {
        ObjectMapper objectMapper = new ObjectMapper();
        return new SoapPhisClient(new PhisSoapCodec(objectMapper), objectMapper);
    }

    /**
     * 创建当前合成服务使用的完整、脱敏调用上下文。
     *
     * @param server 合成HTTP服务
     * @return 协议客户端调用上下文
     */
    private PhisInvocationContext context(HttpServer server) {
        return PhisInvocationContext.create(
                serviceUri(server), 1_000, 3_000, "SYNTHETIC-AUTH");
    }

    /**
     * 创建包含完整机构和分页范围的HIS目录查询。
     *
     * @return 不包含真实机构信息的合成目录查询
     */
    private HospitalDirectoryQuery query() {
        return new HospitalDirectoryQuery(HospitalDirectoryType.DEPARTMENT, null, null);
    }

    /**
     * 启动仅监听回环地址的合成HTTP服务。
     *
     * @param handler 请求处理逻辑
     * @return 已启动服务
     * @throws IOException 服务创建失败时抛出
     */
    private HttpServer startServer(ThrowingHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/WebService.asmx", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception exception) {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    /**
     * 构造合成服务地址。
     *
     * @param server 合成HTTP服务
     * @return 不含操作查询参数的WebService地址
     */
    private URI serviceUri(HttpServer server) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/WebService.asmx");
    }

    /**
     * 返回合成HTTP响应。
     *
     * @param exchange 当前请求
     * @param status HTTP状态
     * @param body 响应正文
     * @throws IOException 响应写入失败时抛出
     */
    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/xml; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /**
     * 构造不含真实数据的SOAP响应。
     *
     * @param json 内层JSON
     * @return SOAP 1.1响应
     */
    private String soapResponse(String json) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soap:Body><PHIS_InterfaceResponse xmlns=\"http://tempuri.org/\">"
                + "<PHIS_InterfaceResult>" + json + "</PHIS_InterfaceResult>"
                + "</PHIS_InterfaceResponse></soap:Body></soap:Envelope>";
    }

    /**
     * 允许测试处理器向外抛出受检异常。
     */
    @FunctionalInterface
    private interface ThrowingHandler {

        /**
         * 处理一次合成HTTP请求。
         *
         * @param exchange 当前请求
         * @throws Exception 处理失败时抛出
         */
        void handle(HttpExchange exchange) throws Exception;
    }
}
