package cn.zqkj.platform.his.client;

import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.domain.protocol.model.PhisTrade;
import cn.zqkj.platform.his.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.his.domain.medicaldirectory.dto.MedicalDirectoryCountQuery;
import cn.zqkj.platform.his.domain.medicaldirectory.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.his.domain.organization.dto.OrganizationQuery;
import cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryEntry;
import cn.zqkj.platform.his.domain.hospitaldirectory.model.HospitalDirectoryRole;
import cn.zqkj.platform.his.domain.medicaldirectory.model.MedicalDirectoryEntry;
import cn.zqkj.platform.his.domain.organization.model.OrganizationEntry;
import cn.zqkj.platform.his.exception.PhisCommunicationException;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.his.exception.PhisRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 使用JDK HTTP客户端适配基层HIS SOAP 1.1服务。
 *
 * <p>公共方法只组装各交易的业务参数；统一调用模板负责注入验证码、发送一次请求、
 * 限制响应大小、转换响应和清除目标系统回显的凭证。调用过程不记录地址、正文或凭证。</p>
 */
@Component
public class SoapPhisClient implements PhisProtocolClient {

    private static final int MAXIMUM_RESPONSE_BYTES = 16 * 1024 * 1024;
    private static final int HTTP_CLIENT_CACHE_LIMIT = 32;
    private static final DateTimeFormatter HIS_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PhisSoapCodec codec;
    private final ObjectMapper objectMapper;
    private final Map<Integer, HttpClient> httpClients = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, HttpClient> eldest) {
            return size() > HTTP_CLIENT_CACHE_LIMIT;
        }
    };

    /**
     * 创建SOAP协议客户端并注入报文编解码和JSON转换依赖。
     *
     * @param codec SOAP编解码器
     * @param objectMapper JSON编码器
     */
    public SoapPhisClient(PhisSoapCodec codec, ObjectMapper objectMapper) {
        this.codec = codec;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>将查询编码为100-003请求并执行一次SOAP调用；错误信息返回前会移除验证码。</p>
     */
    @Override
    public PhisResponse<List<HospitalDirectoryEntry>> queryHospitalDirectory(
            PhisInvocationContext context,
            HospitalDirectoryQuery query
    ) {
        PhisRequestValidator.validate(query);
        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("目录类型", query.directoryType().code());
        putOptionalText(parameters, "目录名称", query.directoryName());
        putOptionalText(parameters, "机构编码", query.sourceOrganizationCode());
        return invoke(context, PhisTrade.HOSPITAL_DIRECTORY_QUERY, parameters, this::mapDirectoryEntries);
    }

    /**
     * {@inheritDoc}
     *
     * <p>将查询编码为100-008请求并执行一次SOAP调用；错误信息返回前会移除验证码。</p>
     */
    @Override
    public PhisResponse<List<OrganizationEntry>> queryOrganizations(
            PhisInvocationContext context,
            OrganizationQuery query
    ) {
        PhisRequestValidator.validate(query);
        ObjectNode parameters = objectMapper.createObjectNode();
        putOptionalText(parameters, "医院名称", query.hospitalName());
        return invoke(context, PhisTrade.ORGANIZATION_QUERY, parameters, this::mapOrganizationEntries);
    }

    /**
     * {@inheritDoc}
     *
     * <p>校验范围和行号后调用100-004；客户端不自动重试，避免调用方误判部分结果。</p>
     */
    @Override
    public PhisResponse<List<MedicalDirectoryEntry>> queryMedicalDirectory(
            PhisInvocationContext context, MedicalDirectoryQuery query
    ) {
        PhisRequestValidator.validate(query);
        ObjectNode parameters = medicalDirectoryRangeParameters(
                query.directoryType().code(), query.directoryName(),
                query.rangeStart().format(HIS_DATE_TIME), query.rangeEnd().format(HIS_DATE_TIME),
                query.sourceOrganizationCode());
        parameters.put("开始行数", query.startRow());
        parameters.put("结束行数", query.endRow());
        return invoke(context, PhisTrade.MEDICAL_DIRECTORY_QUERY, parameters, this::mapMedicalDirectoryEntries);
    }

    /**
     * {@inheritDoc}
     *
     * <p>使用与100-004相同的范围字段调用100-005，保证声明数量可用于完整性校验。</p>
     */
    @Override
    public PhisResponse<Long> countMedicalDirectory(
            PhisInvocationContext context, MedicalDirectoryCountQuery query
    ) {
        PhisRequestValidator.validate(query);
        ObjectNode parameters = medicalDirectoryRangeParameters(
                query.directoryType().code(), query.directoryName(),
                query.rangeStart().format(HIS_DATE_TIME), query.rangeEnd().format(HIS_DATE_TIME),
                query.sourceOrganizationCode());
        return invoke(context, PhisTrade.MEDICAL_DIRECTORY_COUNT, parameters, this::mapDeclaredCount);
    }

    /**
     * 构建100-004与100-005必须共用的查询范围参数。
     *
     * @return 100-004与100-005必须保持一致的范围参数
     */
    private ObjectNode medicalDirectoryRangeParameters(
            String directoryType, String directoryName,
            String rangeStart, String rangeEnd, String sourceOrganizationCode
    ) {
        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("目录类型", directoryType);
        // 该部署在目录名称键缺失时会触发服务端空引用；按文档允许值为空，但必须保留字段。
        parameters.put("目录名称", directoryName == null ? "" : directoryName.trim());
        parameters.put("开始时间", rangeStart);
        parameters.put("结束时间", rangeEnd);
        putOptionalText(parameters, "机构编码", sourceOrganizationCode);
        return parameters;
    }

    /**
     * 清除目标系统在错误说明中回显的机构授权码。
     *
     * @param message 目标错误说明
     * @param authorizationCode 本次调用使用的授权码
     * @return 不包含授权码的错误说明
     */
    private String redactCredential(String message, String authorizationCode) {
        if (message == null || message.isBlank() || authorizationCode == null || authorizationCode.isBlank()) {
            return message;
        }
        return message.replace(authorizationCode, "***");
    }

    /**
     * 注入认证信息并统一执行一次已登记交易的发送、失败脱敏和强类型响应转换。
     *
     * @param context 不可拆分的本次调用上下文
     * @param trade 已登记交易
     * @param businessParameters 不含验证码的业务参数
     * @param responseMapper 成功业务数据转换器
     * @param <T> 强类型业务数据
     * @return 成功或明确业务失败响应
     */
    private <T> PhisResponse<T> invoke(
            PhisInvocationContext context,
            PhisTrade trade,
            ObjectNode businessParameters,
            Function<JsonNode, T> responseMapper
    ) {
        if (context == null || trade == null || businessParameters == null || responseMapper == null) {
            throw new PhisRequestException("基层HIS调用上下文、交易和转换规则不能为空");
        }
        ObjectNode parameters = businessParameters.deepCopy();
        parameters.put("验证码", context.authorizationCode());
        String soapRequest;
        try {
            soapRequest = codec.encodeRequest(trade.code(), parameters.toString());
        } catch (PhisProtocolException exception) {
            throw new PhisRequestException("无法生成基层HIS请求", exception);
        }
        PhisResponse<JsonNode> response = sendSoap(context, soapRequest);
        if (!response.success()) {
            return PhisResponse.failure(response.resultCode(),
                    redactCredential(response.errorMessage(), context.authorizationCode()));
        }
        return PhisResponse.success(response.resultCode(), responseMapper.apply(response.data()));
    }

    /**
     * 发送一次SOAP请求并限制响应正文大小。
     *
     * @param context 已校验调用上下文
     * @param soapRequest 已安全编码的SOAP请求
     * @return 尚未转换具体业务类型的协议响应
     */
    private PhisResponse<JsonNode> sendSoap(PhisInvocationContext context, String soapRequest) {
        HttpRequest request = HttpRequest.newBuilder(context.soapEndpoint())
                .timeout(Duration.ofMillis(context.requestTimeoutMs()))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"" + PhisSoapCodec.SOAP_ACTION + "\"")
                .POST(HttpRequest.BodyPublishers.ofString(soapRequest, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<InputStream> response = httpClient(context.connectTimeoutMs()).send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new PhisCommunicationException("基层HIS返回非成功HTTP状态：" + response.statusCode());
                }
                byte[] bytes = body.readNBytes(MAXIMUM_RESPONSE_BYTES + 1);
                if (bytes.length > MAXIMUM_RESPONSE_BYTES) {
                    throw new PhisProtocolException("基层HIS响应超过允许的安全大小");
                }
                return codec.decodeResponse(new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (HttpTimeoutException exception) {
            throw new PhisCommunicationException("基层HIS调用超时，结果未知", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PhisCommunicationException("基层HIS调用被中断，结果未知", exception);
        } catch (IOException exception) {
            throw new PhisCommunicationException("基层HIS通信失败，结果未知", exception);
        }
    }

    /** 按连接超时复用JDK HTTP客户端，并限制缓存中不同超时配置的数量。 */
    private HttpClient httpClient(int connectTimeoutMs) {
        synchronized (httpClients) {
            return httpClients.computeIfAbsent(connectTimeoutMs, timeout -> HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeout))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build());
        }
    }

    /**
     * 仅在可选文本非空时写入HIS业务参数。
     *
     * @param parameters 业务参数
     * @param fieldName 字段名
     * @param value 可选值
     */
    private void putOptionalText(ObjectNode parameters, String fieldName, String value) {
        if (value != null && !value.isBlank()) {
            parameters.put(fieldName, value.trim());
        }
    }

    /**
     * 把100-003原始JSON数组转换为医院综合目录条目。
     *
     * @param data 100-003原始数据
     * @return 强类型目录列表
     */
    private List<HospitalDirectoryEntry> mapDirectoryEntries(JsonNode data) {
        if (data == null || !data.isArray()) {
            throw new PhisProtocolException("基层HIS医院综合目录响应不是数组");
        }
        List<HospitalDirectoryEntry> entries = new ArrayList<>();
        for (JsonNode item : data) {
            if (!item.isObject()) {
                throw new PhisProtocolException("基层HIS医院综合目录条目不是对象");
            }
            entries.add(new HospitalDirectoryEntry(
                    hospitalDirectoryText(item, "目录编码"), hospitalDirectoryText(item, "目录名称"),
                    hospitalDirectoryText(item, "助记码"), hospitalDirectoryText(item, "目录类别名称"),
                    hospitalDirectoryText(item, "备注"), hospitalDirectoryText(item, "科室编码"),
                    hospitalDirectoryText(item, "科室名称"), hospitalDirectoryText(item, "病区"),
                    hospitalDirectoryText(item, "机构编码"), hospitalDirectoryText(item, "国家医保编码"),
                    hospitalDirectoryText(item, "用户账号"), hospitalDirectoryText(item, "医执人员执业证书编码"),
                    hospitalDirectoryText(item, "医执人员资格证书编码"), hospitalDirectoryText(item, "身份证"),
                    hospitalDirectoryText(item, "医执人员类别"), hospitalDirectoryText(item, "医护人员类型"),
                    hospitalDirectoryText(item, "照片"), hospitalDirectoryText(item, "简介"),
                    mapRoles(item.get("角色")), hospitalDirectoryText(item, "职称编码"),
                    hospitalDirectoryText(item, "职称名称"), hospitalDirectoryText(item, "联系电话"),
                    hospitalDirectoryText(item, "性别")
            ));
        }
        return List.copyOf(entries);
    }

    /**
     * 把100-008原始JSON数组转换为来源机构资料。
     *
     * @param data 100-008原始数据
     * @return 强类型来源机构列表
     */
    private List<OrganizationEntry> mapOrganizationEntries(JsonNode data) {
        if (data == null || !data.isArray()) {
            throw new PhisProtocolException("基层HIS医疗机构响应不是数组");
        }
        List<OrganizationEntry> entries = new ArrayList<>();
        for (JsonNode item : data) {
            if (!item.isObject()) {
                throw new PhisProtocolException("基层HIS医疗机构条目不是对象");
            }
            entries.add(new OrganizationEntry(
                    organizationText(item, "ID"), organizationText(item, "医院名称"),
                    organizationText(item, "地址"), organizationText(item, "联系电话"),
                    organizationText(item, "邮政编码"), organizationText(item, "联系人")
            ));
        }
        return List.copyOf(entries);
    }

    /**
     * 把100-004原始JSON数组转换为医疗目录条目。
     *
     * @param data 100-004原始数据
     * @return 强类型目录列表
     */
    private List<MedicalDirectoryEntry> mapMedicalDirectoryEntries(JsonNode data) {
        if (data == null || !data.isArray()) {
            throw new PhisProtocolException("基层HIS医院三大目录响应不是数组");
        }
        List<MedicalDirectoryEntry> entries = new ArrayList<>();
        for (JsonNode item : data) {
            if (!item.isObject()) throw new PhisProtocolException("基层HIS医院三大目录条目不是对象");
            entries.add(new MedicalDirectoryEntry(
                    medicalDirectoryText(item, "目录编码"), medicalDirectoryText(item, "目录名称"),
                    medicalDirectoryText(item, "助记码"), medicalDirectoryText(item, "目录类别名称"),
                    medicalDirectoryText(item, "单位"), medicalDirectoryText(item, "规格"),
                    medicalDirectoryText(item, "剂型"), medicalDirectoryText(item, "生产厂家名称"),
                    medicalDirectoryText(item, "备注"), medicalDirectoryText(item, "创建时间"),
                    medicalDirectoryText(item, "包装单位"), medicalDirectoryText(item, "转换系数"),
                    medicalDirectoryText(item, "国药准字号"), medicalDirectoryText(item, "药品本位码"),
                    medicalDirectoryText(item, "包装材质"), medicalDirectoryText(item, "炮制方法"),
                    medicalDirectoryText(item, "地区"), medicalDirectoryText(item, "类别"),
                    medicalDirectoryText(item, "是否启用")
            ));
        }
        return List.copyOf(entries);
    }

    /**
     * 解析并校验100-005返回的非负声明数量。
     *
     * @param data 100-005原始数据
     * @return 非负声明行数
     */
    private long mapDeclaredCount(JsonNode data) {
        if (data == null || !data.isArray() || data.size() != 1 || !data.get(0).isObject()) {
            throw new PhisProtocolException("基层HIS医院三大目录行数响应结构无效");
        }
        JsonNode value = data.get(0).get("行数");
        if (value == null || !(value.isIntegralNumber() || value.isTextual())) {
            throw new PhisProtocolException("基层HIS医院三大目录行数无效");
        }
        try {
            long count = Long.parseLong(value.asText().trim());
            if (count < 0) throw new NumberFormatException("negative");
            return count;
        } catch (NumberFormatException exception) {
            throw new PhisProtocolException("基层HIS医院三大目录行数不是非负整数", exception);
        }
    }

    /**
     * 按真实HIS响应解析医生角色数组；文档把该字段描述为VARCHAR，与实际结构不一致。
     *
     * @param data 角色字段
     * @return 不可变角色列表
     */
    private List<HospitalDirectoryRole> mapRoles(JsonNode data) {
        if (data == null || data.isNull()) {
            return List.of();
        }
        if (!data.isArray()) {
            throw new PhisProtocolException("基层HIS医院综合目录字段类型错误：角色（" + data.getNodeType() + "）");
        }
        List<HospitalDirectoryRole> roles = new ArrayList<>();
        for (JsonNode role : data) {
            if (!role.isObject()) {
                throw new PhisProtocolException("基层HIS医生角色条目不是对象");
            }
            roles.add(new HospitalDirectoryRole(
                    hospitalDirectoryText(role, "角色编码"),
                    hospitalDirectoryText(role, "角色名称"),
                    hospitalDirectoryText(role, "创建时间"),
                    hospitalDirectoryText(role, "更新时间")
            ));
        }
        return List.copyOf(roles);
    }

    /**
     * 读取可空文本，并兼容真实基层HIS把文档中的VARCHAR字段返回为数字或布尔标量。
     *
     * @param item 目录条目
     * @param fieldName 字段名
     * @param responseName 当前交易的响应名称，用于生成准确错误说明
     * @return 规范化后的可空文本
     */
    private String optionalText(JsonNode item, String fieldName, String responseName) {
        JsonNode value = item.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!(value.isTextual() || value.isNumber() || value.isBoolean())) {
            throw new PhisProtocolException(
                    "基层HIS" + responseName + "字段类型错误：" + fieldName + "（" + value.getNodeType() + "）");
        }
        return value.asText();
    }

    /**
     * 读取100-003医院综合目录的可空标量字段。
     *
     * @param item 目录条目
     * @param fieldName 字段名
     * @return 规范化后的可空文本
     */
    private String hospitalDirectoryText(JsonNode item, String fieldName) {
        return optionalText(item, fieldName, "医院综合目录");
    }

    /**
     * 读取100-004医院三大目录的可空标量字段。
     *
     * @param item 目录条目
     * @param fieldName 字段名
     * @return 规范化后的可空文本
     */
    private String medicalDirectoryText(JsonNode item, String fieldName) {
        return optionalText(item, fieldName, "医院三大目录");
    }

    /**
     * 读取100-008医疗机构响应的可空标量字段。
     *
     * @param item 机构条目
     * @param fieldName 字段名
     * @return 规范化后的可空文本
     */
    private String organizationText(JsonNode item, String fieldName) {
        return optionalText(item, fieldName, "医疗机构响应");
    }

}
