package cn.zqkj.platform.framework.database;

/**
 * 一项数据库契约差异的受控DDL生成结果。
 *
 * @param executable 是否允许在线执行
 * @param sql 仅由服务端根据可信字段元数据生成的DDL；不可执行时为空
 * @param reason 执行边界或拒绝自动执行的原因
 */
public record DatabaseContractRepairSql(boolean executable, String sql, String reason) {
}
