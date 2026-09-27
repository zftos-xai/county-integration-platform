package cn.zqkj.platform.his.domain.lis.dto;

/**
 * 600-001 LIS 项目目录查询条件。
 *
 * @param organizationCode 基层 HIS 中登记的机构编码
 * @param packageType HIS 项目包类型，例如检验
 */
public record LisItemQuery(String organizationCode, String packageType) {
}
