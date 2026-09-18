package cn.zqkj.platform.his.client;

import cn.zqkj.platform.his.domain.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 使用JDK HTTP客户端适配基层HIS SOAP 1.1服务。
 *
 * <p>调用过程不记录地址、请求正文、响应正文或凭证。</p>
 */
@Component
public class SoapPhisClient implements PhisClient {

    private static final int MIN_TIMEOUT_MS = 100;
    private static final int MAX_CONNECT_TIMEOUT_MS = 60_000;
    private static final int MAX_READ_TIMEOUT_MS = 300_000;

    private final PhisSoapCodec codec;

    /** @param codec SOAP编解码器 */
    public SoapPhisClient(PhisSoapCodec codec) {
        this.codec = codec;
    }

    /** {@inheritDoc} */
    @Override
    public PhisResponse invoke(
            URI serviceUri,
            int connectTimeoutMs,
            int readTimeoutMs,
            String tradeCode,
            String inputParameter
    ) {
        validateServiceUri(serviceUri);
        validateTimeouts(connectTimeoutMs, readTimeoutMs);
        String soapRequest = codec.encodeRequest(tradeCode, inputParameter);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder(serviceUri)
                .timeout(Duration.ofMillis(readTimeoutMs))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"" + PhisSoapCodec.SOAP_ACTION + "\"")
                .POST(HttpRequest.BodyPublishers.ofString(soapRequest, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PhisCommunicationException("基层HIS返回非成功HTTP状态：" + response.statusCode());
            }
            return codec.decodeResponse(response.body());
        } catch (HttpTimeoutException exception) {
            throw new PhisCommunicationException("基层HIS调用超时，结果未知", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PhisCommunicationException("基层HIS调用被中断，结果未知", exception);
        } catch (IOException exception) {
            throw new PhisCommunicationException("基层HIS通信失败，结果未知", exception);
        }
    }

    /** @param serviceUri WebService地址 */
    private void validateServiceUri(URI serviceUri) {
        if (serviceUri == null) {
            throw new PhisCommunicationException("必须提供基层HIS服务地址");
        }
        boolean supportedScheme = "http".equalsIgnoreCase(serviceUri.getScheme())
                || "https".equalsIgnoreCase(serviceUri.getScheme());
        if (!supportedScheme || serviceUri.getHost() == null || serviceUri.getUserInfo() != null
                || serviceUri.getQuery() != null || serviceUri.getFragment() != null) {
            throw new PhisCommunicationException(
                    "基层HIS服务地址必须使用HTTP或HTTPS，且不能包含账号、密码、查询参数或片段");
        }
    }

    /** @param connectTimeoutMs 连接超时毫秒 @param readTimeoutMs 读取超时毫秒 */
    private void validateTimeouts(int connectTimeoutMs, int readTimeoutMs) {
        if (connectTimeoutMs < MIN_TIMEOUT_MS || connectTimeoutMs > MAX_CONNECT_TIMEOUT_MS) {
            throw new PhisCommunicationException("基层HIS连接超时配置超出允许范围");
        }
        if (readTimeoutMs < connectTimeoutMs || readTimeoutMs > MAX_READ_TIMEOUT_MS) {
            throw new PhisCommunicationException("基层HIS读取超时配置超出允许范围");
        }
    }
}
