package cn.zqkj.platform.his.domain.lis.dto;

/**
 * 600-002 LIS 申请查询条件。
 *
 * @param applicationId 可选申请单 ID
 * @param businessId 可选业务 ID
 * @param outpatientNumber 可选门诊号
 * @param inpatientNumber 可选住院号
 */
public record LisApplicationQuery(
        String applicationId,
        String businessId,
        String outpatientNumber,
        String inpatientNumber
) {
}
