package cn.zqkj.platform.his.domain.dto;

/**
 * 表示100-008医疗机构信息查询条件，不包含由服务端注入的HIS验证码。
 *
 * @param hospitalName 可选医院名称；为空时由HIS返回当前授权范围内机构
 */
public record OrganizationQuery(String hospitalName) {
}
