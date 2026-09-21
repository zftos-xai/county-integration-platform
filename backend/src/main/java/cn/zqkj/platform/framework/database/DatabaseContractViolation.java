package cn.zqkj.platform.framework.database;

/**
 * 描述一项启动期数据库契约差异及其建议修改方向。
 *
 * @param code 稳定错误码
 * @param direction 建议修改方向
 * @param objectName 发生差异的数据库或映射对象
 * @param expected 应符合的契约
 * @param actual 实际检测结果
 * @param action 面向开发和运维人员的处理建议
 */
public record DatabaseContractViolation(
        String code,
        Direction direction,
        String objectName,
        String expected,
        String actual,
        String action
) {

    /** 数据库契约差异的建议修改方向。 */
    public enum Direction {
        /** 真实数据库没有落实当前Flyway迁移契约。 */
        DATABASE,
        /** MyBatis映射或Java结果模型与当前契约不一致。 */
        MAPPER_OR_MODEL,
        /** 现有证据无法安全判断唯一修改方向。 */
        MANUAL_REVIEW
    }

    /**
     * 返回单行、可直接复制到问题单的差异说明。
     *
     * @return 中文差异报告
     */
    public String format() {
        return "[" + code + "][" + direction + "] " + objectName
                + " | 期望: " + expected
                + " | 实际: " + actual
                + " | 建议: " + action;
    }
}
