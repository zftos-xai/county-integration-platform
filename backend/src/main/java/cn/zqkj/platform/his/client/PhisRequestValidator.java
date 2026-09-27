package cn.zqkj.platform.his.client;

import cn.zqkj.platform.his.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.his.domain.icd10.dto.Icd10CountQuery;
import cn.zqkj.platform.his.domain.icd10.dto.Icd10Query;
import cn.zqkj.platform.his.domain.lis.dto.LisItemQuery;
import cn.zqkj.platform.his.domain.lis.dto.LisApplicationQuery;
import cn.zqkj.platform.his.domain.lis.dto.LisReportWrite;
import cn.zqkj.platform.his.domain.medicaldirectory.dto.MedicalDirectoryCountQuery;
import cn.zqkj.platform.his.domain.medicaldirectory.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.his.domain.organization.dto.OrganizationQuery;
import cn.zqkj.platform.his.exception.PhisRequestException;

/**
 * 集中校验基层HIS强类型查询，保证业务服务和协议客户端使用同一发送前规则。
 */
public final class PhisRequestValidator {

    private static final int MAXIMUM_PAGE_SIZE = 100;

    /** 禁止实例化无状态校验器。 */
    private PhisRequestValidator() {
    }

    /**
     * 校验600-001 LIS项目目录查询。
     *
     * @param query 查询条件
     * @throws PhisRequestException 机构编码或包类型不符合公版协议时抛出
     */
    public static void validate(LisItemQuery query) {
        if (query == null || query.organizationCode() == null || query.organizationCode().isBlank()) {
            throw new PhisRequestException("必须提供LIS项目目录机构编码");
        }
        if (query.organizationCode().trim().length() > 50) {
            throw new PhisRequestException("LIS项目目录机构编码不能超过50个字符");
        }
        validateLisItemPackageType(query.packageType());
    }

    /**
     * 校验600-001项目包类型值域。
     *
     * @param packageType 公版协议项目包类型
     * @throws PhisRequestException 类型为空、超长或不属于已确认公版值域时抛出
     */
    public static void validateLisItemPackageType(String packageType) {
        if (packageType == null || packageType.isBlank()) {
            throw new PhisRequestException("必须提供LIS项目包类型");
        }
        String normalizedPackageType = packageType.trim();
        if (normalizedPackageType.length() > 50
                || !("检验".equals(normalizedPackageType) || "检查".equals(normalizedPackageType)
                || "体检".equals(normalizedPackageType))) {
            throw new PhisRequestException("LIS项目包类型不符合基层HIS协议");
        }
    }

    /**
     * 校验600-002 LIS申请查询。
     *
     * <p>公版允许四种申请标识为空，但没有定义多字段组合语义。平台要求至少提供一种标识，
     * 防止机构级空条件查询意外返回大批患者申请。</p>
     *
     * @param query 查询条件
     * @throws PhisRequestException 查询标识不符合平台安全边界时抛出
     */
    public static void validate(LisApplicationQuery query) {
        if (query == null) {
            throw new PhisRequestException("必须提供LIS申请查询条件");
        }
        validateOptionalLength("LIS申请单ID", query.applicationId(), 32);
        validateOptionalLength("LIS业务ID", query.businessId(), 32);
        validateOptionalLength("LIS门诊号", query.outpatientNumber(), 50);
        validateOptionalLength("LIS住院号", query.inpatientNumber(), 50);
        if (isBlank(query.applicationId()) && isBlank(query.businessId())
                && isBlank(query.outpatientNumber()) && isBlank(query.inpatientNumber())) {
            throw new PhisRequestException("必须至少提供一种LIS申请查询标识");
        }
    }

    /**
     * 校验600-003已编码报告字符串。
     *
     * <p>该对象属于平台到基层HIS的协议层；只拒绝缺失内容，不解析、改写或再次编码正文。</p>
     *
     * @param query 报告写入参数
     * @throws PhisRequestException 报告内容为空时抛出
     */
    public static void validate(LisReportWrite query) {
        if (query == null || query.encodedFhirReport() == null || query.encodedFhirReport().isBlank()) {
            throw new PhisRequestException("必须提供已编码的LIS检验报告");
        }
    }

    /**
     * 校验100-003医院综合目录查询。
     *
     * @param query 查询条件
     * @throws PhisRequestException 查询条件不完整或超过协议长度时抛出
     */
    public static void validate(HospitalDirectoryQuery query) {
        if (query == null || query.directoryType() == null) {
            throw new PhisRequestException("必须选择医院综合目录类型");
        }
        validateOptionalLength("目录名称", query.directoryName(), 20);
        validateOptionalLength("机构编码", query.sourceOrganizationCode(), 50);
    }

    /**
     * 校验100-004医院三大目录分页查询。
     *
     * @param query 分页查询条件
     * @throws PhisRequestException 范围、分页或字段长度不符合协议时抛出
     */
    public static void validate(MedicalDirectoryQuery query) {
        if (query == null || query.directoryType() == null || query.rangeStart() == null || query.rangeEnd() == null) {
            throw new PhisRequestException("必须提供医院目录类型和查询时间范围");
        }
        if (query.startRow() < 1 || query.endRow() < query.startRow()) {
            throw new PhisRequestException("医院目录分页范围无效");
        }
        if (query.endRow() - query.startRow() >= MAXIMUM_PAGE_SIZE) {
            throw new PhisRequestException("医院目录单页不能超过" + MAXIMUM_PAGE_SIZE + "行");
        }
        validateRange(query.directoryName(), query.rangeStart(), query.rangeEnd(), query.sourceOrganizationCode());
    }

    /**
     * 校验100-005医院三大目录数量查询。
     *
     * @param query 数量查询条件
     * @throws PhisRequestException 范围或字段长度不符合协议时抛出
     */
    public static void validate(MedicalDirectoryCountQuery query) {
        if (query == null || query.directoryType() == null || query.rangeStart() == null || query.rangeEnd() == null) {
            throw new PhisRequestException("必须提供医院目录类型和查询时间范围");
        }
        validateRange(query.directoryName(), query.rangeStart(), query.rangeEnd(), query.sourceOrganizationCode());
    }

    /**
     * 校验100-006 ICD10分页查询。
     *
     * @param query 分页查询条件
     * @throws PhisRequestException 范围、分页或字段长度不符合接口文档时抛出
     */
    public static void validate(Icd10Query query) {
        if (query == null || query.rangeStart() == null || query.rangeEnd() == null) {
            throw new PhisRequestException("必须提供ICD10查询时间范围");
        }
        if (query.startRow() < 1 || query.endRow() <= query.startRow()) {
            throw new PhisRequestException("ICD10分页范围无效");
        }
        // 100-006的TEST端点实测结束行不包含，因此差值就是请求行数。
        if (query.endRow() - query.startRow() > MAXIMUM_PAGE_SIZE) {
            throw new PhisRequestException("ICD10单页不能超过" + MAXIMUM_PAGE_SIZE + "行");
        }
        validateIcd10Range(query.diseaseName(), query.rangeStart(), query.rangeEnd(), query.diagnosisVersion());
    }

    /**
     * 校验100-007 ICD10声明行数查询。
     *
     * @param query 数量查询条件
     * @throws PhisRequestException 范围或字段长度不符合接口文档时抛出
     */
    public static void validate(Icd10CountQuery query) {
        if (query == null || query.rangeStart() == null || query.rangeEnd() == null) {
            throw new PhisRequestException("必须提供ICD10查询时间范围");
        }
        validateIcd10Range(query.diseaseName(), query.rangeStart(), query.rangeEnd(), query.diagnosisVersion());
    }

    /**
     * 校验100-008医疗机构查询。
     *
     * @param query 医疗机构查询条件
     * @throws PhisRequestException 查询对象为空或医院名称过长时抛出
     */
    public static void validate(OrganizationQuery query) {
        if (query == null) {
            throw new PhisRequestException("必须提供医疗机构查询条件");
        }
        validateOptionalLength("医院名称", query.hospitalName(), 100);
    }

    /** 校验100-004和100-005共用的时间及文本范围。 */
    private static void validateRange(
            String directoryName,
            java.time.LocalDateTime rangeStart,
            java.time.LocalDateTime rangeEnd,
            String sourceOrganizationCode
    ) {
        if (rangeEnd.isBefore(rangeStart)) {
            throw new PhisRequestException("查询结束时间不能早于开始时间");
        }
        validateOptionalLength("目录名称", directoryName, 20);
        validateOptionalLength("机构编码", sourceOrganizationCode, 50);
    }

    /** 校验100-006和100-007共用的名称、时间和可选版本条件。 */
    private static void validateIcd10Range(
            String diseaseName,
            java.time.LocalDateTime rangeStart,
            java.time.LocalDateTime rangeEnd,
            String diagnosisVersion
    ) {
        if (rangeEnd.isBefore(rangeStart)) {
            throw new PhisRequestException("ICD10查询结束时间不能早于开始时间");
        }
        validateOptionalLength("病种名称", diseaseName, 20);
        validateOptionalLength("诊断版本", diagnosisVersion, 50);
    }

    /** 校验可选文本的裁剪后长度。 */
    private static void validateOptionalLength(String fieldName, String value, int maximumLength) {
        if (value != null && !value.isBlank() && value.trim().length() > maximumLength) {
            throw new PhisRequestException(fieldName + "不能超过" + maximumLength + "个字符");
        }
    }

    /** 判断可选查询文本是否为空白。 */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
