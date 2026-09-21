package cn.zqkj.platform.databasecontract.domain.vo;

/**
 * 数据库契约维护方案差异明细输出。
 *
 * @param id 主键
 * @param violationCode 差异代码
 * @param objectName 对象名
 * @param expectedValue 期望值
 * @param actualValue 实际值
 * @param direction 修改方向
 * @param actionText 处理建议
 * @param executable 是否可执行
 * @param ddlPreview DDL预览
 * @param executionNote 执行说明
 * @param executionStatus 执行状态
 * @param verificationStatus 复验状态
 */
public record DatabaseContractPlanItemVO(
        long id, String violationCode, String objectName, String expectedValue,
        String actualValue, String direction, String actionText, boolean executable,
        String ddlPreview, String executionNote, String executionStatus,
        String verificationStatus) {
}
