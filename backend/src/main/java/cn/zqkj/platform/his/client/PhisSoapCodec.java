package cn.zqkj.platform.his.client;

import cn.zqkj.platform.his.domain.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;

/**
 * 编解码基层HIS {@code PHIS_Interface}的SOAP 1.1报文。
 *
 * <p>实际WSDL确认操作命名空间为{@code http://tempuri.org/}，入参为{@code TradeCode}和
 * {@code InputParameter}。解码兼容历史响应中的字段名大小写和数字/字符串结果码，但不猜测
 * 0、1以外的成功含义。</p>
 */
@Component
public class PhisSoapCodec {

    /** 实际WSDL确认的SOAP操作。 */
    public static final String SOAP_ACTION = "http://tempuri.org/PHIS_Interface";

    private static final String SOAP_ENVELOPE_NAMESPACE = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SERVICE_NAMESPACE = "http://tempuri.org/";
    private static final String RESPONSE_ELEMENT = "PHIS_InterfaceResult";
    private static final String RESULT_FIELD = "result";
    private static final String MESSAGE_FIELD = "msg";

    private final ObjectMapper objectMapper;

    /**
     * 创建编解码器。
     *
     * @param objectMapper 平台统一JSON解析器
     */
    public PhisSoapCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将交易码和JSON业务参数编码为SOAP 1.1请求。
     *
     * @param tradeCode 正式交易码，例如100-001
     * @param inputParameter JSON业务参数
     * @return UTF-8 SOAP请求文本
     * @throws PhisProtocolException 参数为空、业务参数不是JSON或XML编码失败时抛出
     */
    public String encodeRequest(String tradeCode, String inputParameter) {
        requireText(tradeCode, "必须提供基层HIS交易码");
        requireText(inputParameter, "必须提供基层HIS业务参数");
        validateJson(inputParameter);

        try {
            StringWriter output = new StringWriter();
            XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeStartElement("soap", "Envelope", SOAP_ENVELOPE_NAMESPACE);
            writer.writeNamespace("soap", SOAP_ENVELOPE_NAMESPACE);
            writer.writeStartElement("soap", "Body", SOAP_ENVELOPE_NAMESPACE);
            writer.writeStartElement("PHIS_Interface");
            writer.writeDefaultNamespace(SERVICE_NAMESPACE);
            writer.writeStartElement("TradeCode");
            writer.writeCharacters(tradeCode.trim());
            writer.writeEndElement();
            writer.writeStartElement("InputParameter");
            writer.writeCharacters(inputParameter);
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.close();
            return output.toString();
        } catch (XMLStreamException exception) {
            throw new PhisProtocolException("无法生成基层HIS SOAP请求", exception);
        }
    }

    /**
     * 将SOAP响应转换为统一业务结果。
     *
     * @param soapXml SOAP响应文本
     * @return 统一业务响应
     * @throws PhisProtocolException XML不安全、结构不完整、内层JSON无效或结果码未知时抛出
     */
    public PhisResponse<JsonNode> decodeResponse(String soapXml) {
        requireText(soapXml, "基层HIS SOAP响应为空");
        Document document = parseXml(soapXml);
        if (document.getElementsByTagNameNS(SOAP_ENVELOPE_NAMESPACE, "Fault").getLength() > 0) {
            throw new PhisProtocolException("基层HIS返回SOAP Fault");
        }

        NodeList results = document.getElementsByTagNameNS(SERVICE_NAMESPACE, RESPONSE_ELEMENT);
        if (results.getLength() != 1) {
            throw new PhisProtocolException("基层HIS SOAP响应缺少唯一PHIS_InterfaceResult");
        }
        String json = results.item(0).getTextContent();
        requireText(json, "基层HIS业务响应为空");
        return parseBusinessResponse(json);
    }

    /**
     * 校验业务参数是合法JSON，避免把未转义文本拼入SOAP请求。
     *
     * @param inputParameter JSON业务参数
     */
    private void validateJson(String inputParameter) {
        try {
            JsonNode root = objectMapper.readTree(inputParameter);
            if (!root.isObject()) {
                throw new PhisProtocolException("基层HIS业务参数必须是JSON对象");
            }
        } catch (JsonProcessingException exception) {
            throw new PhisProtocolException("基层HIS业务参数不是合法JSON", exception);
        }
    }

    /**
     * 使用禁止外部实体和文档类型声明的解析器读取SOAP响应。
     *
     * @param soapXml SOAP响应文本
     * @return 安全解析后的XML文档
     */
    private Document parseXml(String soapXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new InputSource(new StringReader(soapXml)));
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            throw new PhisProtocolException("基层HIS SOAP响应无法安全解析", exception);
        }
    }

    /**
     * 兼容解析内层JSON响应的字段大小写和结果码类型。
     *
     * @param json 内层JSON文本
     * @return 统一业务响应
     */
    private PhisResponse<JsonNode> parseBusinessResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()) {
                throw new PhisProtocolException("基层HIS业务响应必须是JSON对象");
            }
            JsonNode result = findFieldIgnoreCase(root, RESULT_FIELD);
            if (result == null || !(result.isIntegralNumber() || result.isTextual())) {
                throw new PhisProtocolException("基层HIS业务响应缺少有效result字段");
            }
            String resultCode = result.asText().trim();
            if (!"0".equals(resultCode) && !"1".equals(resultCode)) {
                throw new PhisProtocolException("基层HIS返回未确认的结果码");
            }
            JsonNode message = findFieldIgnoreCase(root, MESSAGE_FIELD);
            boolean success = "1".equals(resultCode);
            if (success) {
                return PhisResponse.success(resultCode,
                        message == null ? NullNode.getInstance() : message);
            }
            String errorMessage = message != null && message.isTextual()
                    ? message.asText()
                    : "基层HIS返回业务失败";
            return PhisResponse.failure(resultCode, errorMessage);
        } catch (JsonProcessingException exception) {
            throw new PhisProtocolException("基层HIS业务响应不是合法JSON", exception);
        }
    }

    /**
     * 不区分大小写查找JSON对象字段。
     *
     * @param object JSON对象
     * @param expectedName 预期字段名
     * @return 字段值；不存在时返回{@code null}
     */
    private JsonNode findFieldIgnoreCase(JsonNode object, String expectedName) {
        Iterator<Map.Entry<String, JsonNode>> fields = object.properties().iterator();
        JsonNode matched = null;
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (expectedName.equalsIgnoreCase(field.getKey())) {
                if (matched != null) {
                    throw new PhisProtocolException("基层HIS业务响应包含重复的" + expectedName + "字段");
                }
                matched = field.getValue();
            }
        }
        return matched;
    }

    /**
     * 校验必填文本。
     *
     * @param value 待校验文本
     * @param message 受控错误说明
     */
    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PhisProtocolException(message);
        }
    }
}
