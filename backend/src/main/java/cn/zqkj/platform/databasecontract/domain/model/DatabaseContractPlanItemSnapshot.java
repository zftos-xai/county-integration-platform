package cn.zqkj.platform.databasecontract.domain.model;

/**
 * 数据库契约维护方案的单项差异快照。
 *
 * <p>对应表：{@code dbo.db_contract_plan_item}（数据库契约维护方案明细表），
 * 通过 {@code plan_id}归属 {@code dbo.db_contract_plan}。</p>
 *
 * <p>业务说明：固化方案生成时的预期值、实际值、处理建议和DDL预览；执行前必须与实时差异再次核对。</p>
 *
 * @param id 主键
 * @param planId 方案主键
 * @param violationCode 差异代码
 * @param objectName 对象名
 * @param expectedValue 期望值
 * @param actualValue 实际值
 * @param direction 修改方向
 * @param actionText 处理建议
 * @param executable 是否可执行
 * @param ddlPreview DDL预览
 * @param executionNote 执行边界
 * @param executionStatus 执行状态
 * @param verificationStatus 复验状态
 */
public record DatabaseContractPlanItemSnapshot(
        long id, long planId, String violationCode, String objectName,
        String expectedValue, String actualValue, String direction, String actionText,
        boolean executable, String ddlPreview, String executionNote,
        String executionStatus, String verificationStatus) {
}
