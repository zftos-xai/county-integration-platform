package cn.zqkj.platform.common.utils.exception;

/**
 * 实现异常链的通用只读分析。
 *
 * <p>本类参考RuoYi异常工具，但不提供堆栈字符串输出，避免业务代码意外记录敏感内部信息；
 * 是{@code Func}门面的分类实现，业务代码不得绕过门面直接调用。</p>
 */
public final class ExceptionUtils {

    /** 异常链最大遍历深度，用于防止异常原因形成环。 */
    private static final int MAXIMUM_CAUSE_DEPTH = 100;

    /**
     * 禁止创建异常工具实现实例。
     */
    private ExceptionUtils() {
    }

    /**
     * 取得异常链最深处的异常。
     *
     * <p>示例：包装异常的原因为{@code IllegalStateException}时返回该原因实例。</p>
     *
     * @param throwable 可选异常
     * @return 根异常；输入为空时返回空
     */
    public static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && current.getCause() != null && current.getCause() != current
                && depth < MAXIMUM_CAUSE_DEPTH) {
            current = current.getCause();
            depth++;
        }
        return current;
    }

    /**
     * 取得根异常的非空简短说明。
     *
     * <p>结果只适合内部诊断，不得未经脱敏直接作为API响应。</p>
     * <p>示例：根异常消息为{@code "连接超时"}时返回同一文本。</p>
     *
     * @param throwable 可选异常
     * @return 根异常消息；消息为空时返回异常简单类名，输入为空时返回空字符串
     */
    public static String rootMessage(Throwable throwable) {
        Throwable root = rootCause(throwable);
        if (root == null) {
            return "";
        }
        return root.getMessage() == null || root.getMessage().isBlank()
                ? root.getClass().getSimpleName()
                : root.getMessage();
    }
}
