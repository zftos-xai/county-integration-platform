package cn.zqkj.platform.masterdata.domain.medicaldirectory.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;

/**
 * 解释机构、环境下已确认的100-004/100-005全量查询下界。
 *
 * <p>数据来源：{@code dbo.sys_parameter_value}（平台参数值表）中的单个规则值；
 * 业务说明：规则必须绑定已验证端点，并保存来源方确认覆盖全部四类目录的依据。
 * 本对象不是独立表行，也不包含接口凭证。</p>
 *
 * @param endpointId 已确认的HIS服务端点主键
 * @param rangeStart 来源方确认的最早包含时间，带明确时区偏移
 * @param evidence 来源方确认时间字段、空时间记录与覆盖边界的非敏感依据
 */
public record MedicalDirectoryFullSyncRule(long endpointId, OffsetDateTime rangeStart, String evidence) {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 解析单条已登记的全量规则；格式或确认依据不完整时拒绝执行。
     *
     * @param value 指定机构、环境下的规则JSON
     * @return 可用于冻结本次范围的规则
     */
    public static MedicalDirectoryFullSyncRule parse(String value) {
        try {
            JsonNode node = JSON.readTree(value);
            if (node == null || !node.isObject() || node.size() != 3
                    || !node.path("endpointId").canConvertToLong()
                    || !node.path("rangeStart").isTextual()
                    || !node.path("evidence").isTextual()) {
                throw new IllegalArgumentException("全量规则字段不完整");
            }
            long endpointId = node.path("endpointId").longValue();
            OffsetDateTime rangeStart = OffsetDateTime.parse(node.path("rangeStart").textValue());
            String evidence = node.path("evidence").textValue().trim();
            if (endpointId <= 0 || evidence.length() < 20 || evidence.length() > 500) {
                throw new IllegalArgumentException("全量规则确认依据不足");
            }
            return new MedicalDirectoryFullSyncRule(endpointId, rangeStart, evidence);
        } catch (Exception exception) {
            throw new IllegalArgumentException("全量规则未完成确认或格式无效", exception);
        }
    }
}
