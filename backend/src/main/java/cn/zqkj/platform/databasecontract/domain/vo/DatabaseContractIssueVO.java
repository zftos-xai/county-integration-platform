package cn.zqkj.platform.databasecontract.domain.vo;

/**
 * 实时数据库契约差异。
 *
 * @param issueKey 稳定差异键
 * @param code 错误码
 * @param direction 修改方向
 * @param objectName 对象名
 * @param expected 期望值
 * @param actual 实际值
 * @param action 处理建议
 * @param executable 是否允许在线执行
 * @param ddlPreview DDL预览
 * @param executionNote 执行边界或拒绝原因
 */
public record DatabaseContractIssueVO(
        String issueKey, String code, String direction, String objectName,
        String expected, String actual, String action, boolean executable,
        String ddlPreview, String executionNote) {
}
