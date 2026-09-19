package cn.zqkj.platform.common.utils.text;

import java.util.ArrayList;
import java.util.List;

/**
 * 实现跨业务且不包含业务规则的文本处理。
 *
 * <p>本类是{@code Func}门面的分类实现，业务代码不得绕过门面直接调用。</p>
 */
public final class TextUtils {

    private static final char MASK_CHARACTER = '*';

    /**
     * 禁止创建文本工具实现实例。
     */
    private TextUtils() {
    }

    /**
     * 裁剪文本并统一空值表示。
     *
     * <p>示例：{@code trimToNull(" value ")}返回{@code "value"}。</p>
     *
     * @param value 可选文本
     * @return 裁剪后的文本；输入为空或仅含空白时返回空
     */
    public static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 判断文本是否为空或仅包含空白字符。
     *
     * <p>示例：{@code isBlank("  ")}返回{@code true}。</p>
     *
     * @param value 可选文本
     * @return 为空或仅含空白字符时返回{@code true}
     */
    public static boolean isBlank(CharSequence value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isWhitespace(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断文本是否包含至少一个非空白字符。
     *
     * <p>示例：{@code isNotBlank(" value ")}返回{@code true}。</p>
     *
     * @param value 可选文本
     * @return 包含非空白字符时返回{@code true}
     */
    public static boolean isNotBlank(CharSequence value) {
        return !isBlank(value);
    }

    /**
     * 使用星号遮盖文本的指定区间。
     *
     * <p>示例：{@code mask("abcdef", 2, 4)}返回{@code "ab**ef"}。</p>
     *
     * @param value 原始文本
     * @param startInclusive 遮盖起始索引，包含该位置
     * @param endExclusive 遮盖结束索引，不包含该位置
     * @return 遮盖后的文本；输入为空时返回空
     * @throws IllegalArgumentException 索引范围无效时抛出
     */
    public static String mask(CharSequence value, int startInclusive, int endExclusive) {
        if (value == null) {
            return null;
        }
        if (startInclusive < 0 || endExclusive < startInclusive || endExclusive > value.length()) {
            throw new IllegalArgumentException("遮盖区间超出文本范围");
        }
        StringBuilder result = new StringBuilder(value);
        for (int index = startInclusive; index < endExclusive; index++) {
            result.setCharAt(index, MASK_CHARACTER);
        }
        return result.toString();
    }

    /**
     * 将驼峰式名称转换为小写下划线名称。
     *
     * <p>示例：{@code toSnakeCase("HTTPServerUrl")}返回{@code "http_server_url"}。</p>
     *
     * @param value 可选驼峰式名称
     * @return 小写下划线名称；输入为空时返回原值
     */
    public static String toSnakeCase(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        StringBuilder result = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            boolean currentUpper = Character.isUpperCase(current);
            boolean previousLower = index > 0 && Character.isLowerCase(value.charAt(index - 1));
            boolean nextLower = index + 1 < value.length() && Character.isLowerCase(value.charAt(index + 1));
            if (currentUpper && index > 0 && (previousLower || nextLower)) {
                result.append('_');
            }
            result.append(Character.toLowerCase(current));
        }
        return result.toString();
    }

    /**
     * 将下划线名称转换为小驼峰名称。
     *
     * <p>示例：{@code toCamelCase("USER_LOGIN_NAME")}返回{@code "userLoginName"}。</p>
     *
     * @param value 可选下划线名称
     * @return 小驼峰名称；输入为空时返回原值
     */
    public static String toCamelCase(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        StringBuilder result = new StringBuilder(value.length());
        boolean uppercaseNext = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '_') {
                uppercaseNext = result.length() > 0;
            } else if (uppercaseNext) {
                result.append(Character.toUpperCase(current));
                uppercaseNext = false;
            } else {
                result.append(Character.toLowerCase(current));
            }
        }
        return result.toString();
    }

    /**
     * 使用支持负索引的区间截取文本。
     *
     * <p>负索引从文本末尾开始计算，超出边界的索引会收敛到合法范围。</p>
     * <p>示例：{@code substring("abcdef", -4, -1)}返回{@code "cde"}。</p>
     *
     * @param value 可选文本
     * @param startInclusive 起始索引，包含该位置
     * @param endExclusive 结束索引，不包含该位置
     * @return 截取结果；输入为空或规范化后区间无效时返回空字符串
     */
    public static String substring(String value, int startInclusive, int endExclusive) {
        if (value == null) {
            return "";
        }
        int start = startInclusive < 0 ? value.length() + startInclusive : startInclusive;
        int end = endExclusive < 0 ? value.length() + endExclusive : endExclusive;
        start = Math.max(0, start);
        end = Math.min(value.length(), Math.max(0, end));
        return start > end ? "" : value.substring(start, end);
    }

    /**
     * 顺序替换文本中的{@code {}}占位符。
     *
     * <p>{@code \{}}保留为字面占位符，{@code \\{}}输出一个反斜杠并替换占位符。</p>
     * <p>示例：{@code format("批次 {}", "B-1")}返回{@code "批次 B-1"}。</p>
     *
     * @param template 模板文本
     * @param arguments 可选替换参数
     * @return 格式化结果；模板为空或没有参数时返回原模板
     */
    public static String format(String template, Object... arguments) {
        if (template == null || template.isEmpty() || arguments == null || arguments.length == 0) {
            return template;
        }
        StringBuilder result = new StringBuilder(template.length() + 32);
        int handledPosition = 0;
        int argumentIndex = 0;
        while (argumentIndex < arguments.length) {
            int placeholderIndex = template.indexOf("{}", handledPosition);
            if (placeholderIndex < 0) {
                break;
            }
            if (placeholderIndex > 0 && template.charAt(placeholderIndex - 1) == '\\') {
                if (placeholderIndex > 1 && template.charAt(placeholderIndex - 2) == '\\') {
                    result.append(template, handledPosition, placeholderIndex - 1);
                    result.append(String.valueOf(arguments[argumentIndex++]));
                    handledPosition = placeholderIndex + 2;
                } else {
                    result.append(template, handledPosition, placeholderIndex - 1).append("{}");
                    handledPosition = placeholderIndex + 2;
                }
            } else {
                result.append(template, handledPosition, placeholderIndex);
                result.append(String.valueOf(arguments[argumentIndex++]));
                handledPosition = placeholderIndex + 2;
            }
        }
        return result.append(template, handledPosition, template.length()).toString();
    }

    /**
     * 按字面分隔符拆分文本，并忽略裁剪后的空元素。
     *
     * <p>示例：{@code split(" A, ,B ", ",")}返回{@code ["A", "B"]}。</p>
     *
     * @param value 可选文本
     * @param delimiter 非空字面分隔符
     * @return 保持原顺序的不可变文本列表
     */
    public static List<String> split(String value, String delimiter) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        if (delimiter == null || delimiter.isEmpty()) {
            throw new IllegalArgumentException("文本分隔符不能为空");
        }
        List<String> result = new ArrayList<>();
        int start = 0;
        int separatorIndex;
        while ((separatorIndex = value.indexOf(delimiter, start)) >= 0) {
            addNonBlank(result, value.substring(start, separatorIndex));
            start = separatorIndex + delimiter.length();
        }
        addNonBlank(result, value.substring(start));
        return List.copyOf(result);
    }

    /**
     * 在文本左侧补充指定字符直至达到最小长度。
     *
     * <p>示例：{@code padLeft("7", 4, '0')}返回{@code "0007"}。</p>
     *
     * @param value 可选原始文本，空值按空字符串处理
     * @param minimumLength 结果最小长度，不得小于零
     * @param padding 补充字符
     * @return 补齐后的文本；原文本已达到长度时原样返回
     */
    public static String padLeft(String value, int minimumLength, char padding) {
        if (minimumLength < 0) {
            throw new IllegalArgumentException("最小长度不能小于零");
        }
        String source = value == null ? "" : value;
        if (source.length() >= minimumLength) {
            return source;
        }
        return String.valueOf(padding).repeat(minimumLength - source.length()) + source;
    }

    /**
     * 裁剪文本并在非空时加入结果列表。
     *
     * @param result 目标列表
     * @param value 候选文本
     */
    private static void addNonBlank(List<String> result, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            result.add(normalized);
        }
    }
}
