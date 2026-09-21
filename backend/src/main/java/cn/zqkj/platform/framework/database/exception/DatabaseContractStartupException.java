package cn.zqkj.platform.framework.database.exception;

import cn.zqkj.platform.framework.database.DatabaseContractViolation;

import java.util.List;

/** 表示启动期数据库契约门禁未通过，并保留可供测试和诊断读取的结构化差异。 */
public class DatabaseContractStartupException extends IllegalStateException {

    /** 导致启动门禁失败的结构化契约差异。 */
    private final List<DatabaseContractViolation> violations;

    /**
     * 创建确定性契约差异导致的启动异常。
     *
     * @param message 启动失败摘要
     * @param violations 结构化差异
     */
    public DatabaseContractStartupException(String message,
                                            List<DatabaseContractViolation> violations) {
        super(message);
        this.violations = List.copyOf(violations);
    }

    /**
     * 创建检查过程异常导致的启动异常。
     *
     * @param message 启动失败摘要
     * @param cause 原始异常
     */
    public DatabaseContractStartupException(String message, Throwable cause) {
        super(message, cause);
        this.violations = List.of();
    }

    /**
     * 返回导致门禁失败的结构化差异。
     *
     * @return 不可变差异集合；检查过程异常时为空
     */
    public List<DatabaseContractViolation> violations() {
        return violations;
    }
}
