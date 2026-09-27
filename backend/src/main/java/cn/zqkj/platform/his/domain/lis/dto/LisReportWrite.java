package cn.zqkj.platform.his.domain.lis.dto;

/**
 * 600-003 LIS 检验报告回写参数。
 *
 * @param encodedFhirReport 平台按基层HIS协议编码后的FHIR报告字符串；仅用于下游协议调用
 */
public record LisReportWrite(String encodedFhirReport) {
}
