package cn.zqkj.platform.his.client;

import cn.zqkj.platform.his.domain.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证基层HIS客户端使用WSDL确认的HTTP、SOAPAction和受控异常边界。
 */
class SoapPhisClientTest {

    /**
     * 验证客户端向WebService基础地址发送SOAP请求并解析成功结果。
     */
    @Test
    void postsSoapRequestToHttpServiceAddress() throws Exception {
        AtomicReference<String> soapAction = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            soapAction.set(exchange.getRequestHeaders().getFirst("SOAPAction"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(exchange, 200, soapResponse("{\"result\":\"1\",\"msg\":\"接口测试成功\"}"));
        });

        try {
            PhisClient client = client();
            URI serviceUri = serviceUri(server);
            PhisResponse response = client.invoke(
                    serviceUri,
                    1_000,
                    3_000,
                    "100-001",
                    "{\"厂商编号\":\"SYNTHETIC\"}"
            );

            assertTrue(response.success());
            assertEquals("\"http://tempuri.org/PHIS_Interface\"", soapAction.get());
            assertTrue(requestBody.get().contains("<TradeCode>100-001</TradeCode>"));
            assertTrue(requestBody.get().contains("<InputParameter>"));
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
                    () -> client().invoke(
                            serviceUri(server),
                            1_000,
                            3_000,
                            "100-001",
                            "{\"厂商编号\":\"SYNTHETIC\"}"
                    )
            );

            assertTrue(exception.getMessage().contains("500"));
            assertTrue(!exception.getMessage().contains("sensitive-target-body"));
        } finally {
            server.stop(0);
        }
    }

    /**
     * 验证客户端拒绝把ASMX操作页查询参数当作SOAP服务地址。
     */
    @Test
    void rejectsOperationPageQueryParameter() {
        PhisClient client = client();

        assertThrows(PhisCommunicationException.class, () -> client.invoke(
                URI.create("http://his.example.invalid/WebService.asmx?op=PHIS_Interface"),
                1_000,
                3_000,
                "100-001",
                "{\"厂商编号\":\"SYNTHETIC\"}"
        ));
    }

    /**
     * 创建使用真实编解码器的客户端。
     *
     * @return 基层HIS客户端
     */
    private PhisClient client() {
        return new SoapPhisClient(new PhisSoapCodec(new ObjectMapper()));
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
