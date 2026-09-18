package cn.zqkj.platform.his.client;

import cn.zqkj.platform.his.domain.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证基层HIS SOAP请求转义、安全解析和历史响应兼容规则。
 */
class PhisSoapCodecTest {

    private final PhisSoapCodec codec = new PhisSoapCodec(new ObjectMapper());

    /**
     * 验证交易码和JSON参数进入WSDL确认的元素，且特殊字符不会破坏XML结构。
     */
    @Test
    void encodesConfirmedOperationAndEscapesJson() throws Exception {
        String request = codec.encodeRequest("100-001", "{\"厂商编号\":\"A&B<1>\"}");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(request)));

        assertEquals("100-001", document.getElementsByTagNameNS("http://tempuri.org/", "TradeCode")
                .item(0).getTextContent());
        assertEquals("{\"厂商编号\":\"A&B<1>\"}",
                document.getElementsByTagNameNS("http://tempuri.org/", "InputParameter")
                        .item(0).getTextContent());
        assertEquals("http://tempuri.org/PHIS_Interface", PhisSoapCodec.SOAP_ACTION);
    }

    /**
     * 验证小写字段和字符串成功码能够转换为成功结果。
     */
    @Test
    void decodesLowercaseStringSuccess() {
        PhisResponse response = codec.decodeResponse(soapResponse(
                "{\"result\":\"1\",\"msg\":{\"验证码\":\"masked\"}}"
        ));

        assertTrue(response.success());
        assertEquals("1", response.resultCode());
        assertEquals("masked", response.message().path("验证码").asText());
    }

    /**
     * 验证大写字段和数字失败码能够转换为明确业务失败。
     */
    @Test
    void decodesUppercaseNumericFailure() {
        PhisResponse response = codec.decodeResponse(soapResponse(
                "{\"Result\":0,\"Msg\":\"凭证无效\"}"
        ));

        assertFalse(response.success());
        assertEquals("0", response.resultCode());
        assertEquals("凭证无效", response.message().asText());
    }

    /**
     * 验证0和1之外的结果码不会被猜测为成功或失败。
     */
    @Test
    void rejectsUnknownResultCode() {
        String response = soapResponse("{\"result\":\"200\",\"msg\":\"ok\"}");

        assertThrows(PhisProtocolException.class, () -> codec.decodeResponse(response));
    }

    /**
     * 验证缺少统一结果字段的响应不能进入业务处理。
     */
    @Test
    void rejectsMissingResult() {
        String response = soapResponse("{\"msg\":\"ok\"}");

        assertThrows(PhisProtocolException.class, () -> codec.decodeResponse(response));
    }

    /**
     * 验证大小写不同但语义相同的重复结果字段不会被按遍历顺序猜测。
     */
    @Test
    void rejectsDuplicateResultFields() {
        String response = soapResponse("{\"result\":\"1\",\"Result\":\"0\",\"msg\":\"ambiguous\"}");

        assertThrows(PhisProtocolException.class, () -> codec.decodeResponse(response));
    }

    /**
     * 验证文档类型声明会被拒绝，防止响应利用外部实体读取本地资源。
     */
    @Test
    void rejectsDocumentTypeDeclaration() {
        String response = "<?xml version=\"1.0\"?><!DOCTYPE root [<!ENTITY xxe SYSTEM \"file:///tmp/x\">]>"
                + "<root>&xxe;</root>";

        assertThrows(PhisProtocolException.class, () -> codec.decodeResponse(response));
    }

    /**
     * 验证非法JSON不会被拼接到SOAP请求。
     */
    @Test
    void rejectsInvalidInputJson() {
        assertThrows(PhisProtocolException.class, () -> codec.encodeRequest("100-001", "not-json"));
    }

    /**
     * 验证合法但不是对象的JSON不能作为接口业务参数。
     */
    @Test
    void rejectsNonObjectInputJson() {
        assertThrows(PhisProtocolException.class, () -> codec.encodeRequest("100-001", "[]"));
    }

    /**
     * 构造不包含真实凭证和业务数据的SOAP测试响应。
     *
     * @param json 内层JSON响应
     * @return SOAP 1.1响应
     */
    private String soapResponse(String json) {
        String escaped = json.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soap:Body><PHIS_InterfaceResponse xmlns=\"http://tempuri.org/\">"
                + "<PHIS_InterfaceResult>" + escaped + "</PHIS_InterfaceResult>"
                + "</PHIS_InterfaceResponse></soap:Body></soap:Envelope>";
    }
}
