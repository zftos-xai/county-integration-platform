package cn.zqkj.platform.common.utils.identifier;

import java.util.Locale;
import java.util.UUID;

/**
 * 实现无业务含义的随机标识符生成。
 *
 * <p>本类参考RuoYi标识符工具，是{@code Func}门面的分类实现，业务代码不得绕过门面直接调用。</p>
 */
public final class IdUtils {

    /**
     * 禁止创建标识符工具实现实例。
     */
    private IdUtils() {
    }

    /**
     * 生成标准小写UUID文本。
     *
     * <p>示例结果：{@code "123e4567-e89b-12d3-a456-426614174000"}。</p>
     *
     * @return 包含连字符的36位UUID文本
     */
    public static String randomUuid() {
        return UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
    }

    /**
     * 生成不包含连字符的小写UUID文本。
     *
     * <p>示例结果：{@code "123e4567e89b12d3a456426614174000"}。</p>
     *
     * @return 不含连字符的32位UUID文本
     */
    public static String simpleUuid() {
        return randomUuid().replace("-", "");
    }
}
