package cn.zqkj.platform.his.client;

import cn.zqkj.platform.his.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
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

    /** 校验可选文本的裁剪后长度。 */
    private static void validateOptionalLength(String fieldName, String value, int maximumLength) {
        if (value != null && !value.isBlank() && value.trim().length() > maximumLength) {
            throw new PhisRequestException(fieldName + "不能超过" + maximumLength + "个字符");
        }
    }
}
