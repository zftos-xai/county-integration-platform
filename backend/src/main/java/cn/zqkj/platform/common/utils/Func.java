package cn.zqkj.platform.common.utils;

import cn.zqkj.platform.common.utils.collection.CollectionUtils;
import cn.zqkj.platform.common.utils.datetime.DateTimeUtils;
import cn.zqkj.platform.common.utils.encoding.EncodingUtils;
import cn.zqkj.platform.common.utils.exception.ExceptionUtils;
import cn.zqkj.platform.common.utils.file.FileNameUtils;
import cn.zqkj.platform.common.utils.identifier.IdUtils;
import cn.zqkj.platform.common.utils.math.ArithmeticUtils;
import cn.zqkj.platform.common.utils.text.TextUtils;
import cn.zqkj.platform.common.utils.validation.ValidationUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 统一提供跨业务且无状态的公共工具方法。
 *
 * <p>业务代码只通过本类调用公共工具；具体实现按照日期时间、文本、集合、编码、标识符、数值和校验等
 * 职责分别维护，避免不同性质的处理逻辑堆积在同一个实现类中。</p>
 */
public final class Func {

    /**
     * 禁止创建公共工具门面实例。
     */
    private Func() {
    }

    /**
     * 将带偏移量的时间转换为数据库使用的UTC本地时间。
     *
     * <p>示例：{@code Func.toUtc(OffsetDateTime.parse("2026-09-19T10:30:00+08:00"))}
     * 返回{@code 2026-09-19T02:30:00}。</p>
     *
     * @param value 可选带偏移量时间
     * @return 表示同一时刻的UTC本地时间；输入为空时返回空
     */
    public static LocalDateTime toUtc(OffsetDateTime value) {
        return DateTimeUtils.toUtc(value);
    }

    /**
     * 将数据库使用的UTC本地时间转换为带UTC偏移量的时间。
     *
     * <p>示例：{@code Func.toOffset(LocalDateTime.parse("2026-09-19T02:30:00"))}
     * 返回{@code 2026-09-19T02:30:00Z}。</p>
     *
     * @param value 可选UTC本地时间
     * @return 带UTC偏移量的时间；输入为空时返回空
     */
    public static OffsetDateTime toOffset(LocalDateTime value) {
        return DateTimeUtils.toOffset(value);
    }

    /**
     * 裁剪文本首尾空白，并将空文本统一转换为空值。
     *
     * <p>示例：{@code Func.trimToNull("  value  ")}返回{@code "value"}；空白文本返回{@code null}。</p>
     *
     * @param value 可选文本
     * @return 裁剪后的文本；输入为空或仅含空白时返回空
     */
    public static String trimToNull(String value) {
        return TextUtils.trimToNull(value);
    }

    /**
     * 判断文本是否为空或仅包含空白字符。
     *
     * <p>示例：{@code Func.isBlank("  ")}返回{@code true}。</p>
     *
     * @param value 可选文本
     * @return 为空或仅含空白字符时返回{@code true}
     */
    public static boolean isBlank(CharSequence value) {
        return TextUtils.isBlank(value);
    }

    /**
     * 判断文本是否包含至少一个非空白字符。
     *
     * <p>示例：{@code Func.isNotBlank(" value ")}返回{@code true}。</p>
     *
     * @param value 可选文本
     * @return 包含非空白字符时返回{@code true}
     */
    public static boolean isNotBlank(CharSequence value) {
        return TextUtils.isNotBlank(value);
    }

    /**
     * 使用星号遮盖文本的指定区间。
     *
     * <p>示例：{@code Func.mask("abcdef", 2, 4)}返回{@code "ab**ef"}。</p>
     *
     * @param value 原始文本
     * @param startInclusive 遮盖起始索引，包含该位置
     * @param endExclusive 遮盖结束索引，不包含该位置
     * @return 遮盖后的文本；输入为空时返回空
     */
    public static String mask(CharSequence value, int startInclusive, int endExclusive) {
        return TextUtils.mask(value, startInclusive, endExclusive);
    }

    /**
     * 将驼峰式名称转换为小写下划线名称。
     *
     * <p>示例：{@code Func.toSnakeCase("HTTPServerUrl")}返回{@code "http_server_url"}。</p>
     *
     * @param value 可选驼峰式名称
     * @return 小写下划线名称；输入为空时返回原值
     */
    public static String toSnakeCase(String value) {
        return TextUtils.toSnakeCase(value);
    }

    /**
     * 将下划线名称转换为小驼峰名称。
     *
     * <p>示例：{@code Func.toCamelCase("USER_LOGIN_NAME")}返回{@code "userLoginName"}。</p>
     *
     * @param value 可选下划线名称
     * @return 小驼峰名称；输入为空时返回原值
     */
    public static String toCamelCase(String value) {
        return TextUtils.toCamelCase(value);
    }

    /**
     * 使用支持负索引的区间截取文本。
     *
     * <p>示例：{@code Func.substring("abcdef", -4, -1)}返回{@code "cde"}。</p>
     *
     * @param value 可选文本
     * @param startInclusive 起始索引，包含该位置
     * @param endExclusive 结束索引，不包含该位置
     * @return 截取结果；输入为空或区间无效时返回空字符串
     */
    public static String substring(String value, int startInclusive, int endExclusive) {
        return TextUtils.substring(value, startInclusive, endExclusive);
    }

    /**
     * 顺序替换文本中的{@code {}}占位符。
     *
     * <p>示例：{@code Func.format("批次 {} 已创建", "B-1")}返回{@code "批次 B-1 已创建"}。</p>
     *
     * @param template 模板文本
     * @param arguments 可选替换参数
     * @return 格式化结果
     */
    public static String format(String template, Object... arguments) {
        return TextUtils.format(template, arguments);
    }

    /**
     * 按字面分隔符拆分文本，并忽略裁剪后的空元素。
     *
     * <p>示例：{@code Func.split(" A, ,B ", ",")}返回{@code ["A", "B"]}。</p>
     *
     * @param value 可选文本
     * @param delimiter 非空字面分隔符
     * @return 保持原顺序的不可变文本列表
     */
    public static List<String> split(String value, String delimiter) {
        return TextUtils.split(value, delimiter);
    }

    /**
     * 在文本左侧补充指定字符直至达到最小长度。
     *
     * <p>示例：{@code Func.padLeft("7", 4, '0')}返回{@code "0007"}。</p>
     *
     * @param value 可选原始文本
     * @param minimumLength 结果最小长度
     * @param padding 补充字符
     * @return 补齐后的文本
     */
    public static String padLeft(String value, int minimumLength, char padding) {
        return TextUtils.padLeft(value, minimumLength, padding);
    }

    /**
     * 判断集合是否为空。
     *
     * <p>示例：{@code Func.isEmpty(List.of())}返回{@code true}。</p>
     *
     * @param value 可选集合
     * @return 集合为空或没有元素时返回{@code true}
     */
    public static boolean isEmpty(Collection<?> value) {
        return CollectionUtils.isEmpty(value);
    }

    /**
     * 判断映射是否为空。
     *
     * <p>示例：{@code Func.isEmpty(Map.of())}返回{@code true}。</p>
     *
     * @param value 可选映射
     * @return 映射为空或没有条目时返回{@code true}
     */
    public static boolean isEmpty(Map<?, ?> value) {
        return CollectionUtils.isEmpty(value);
    }

    /**
     * 判断对象数组是否为空。
     *
     * <p>示例：{@code Func.isEmpty(new Object[0])}返回{@code true}。</p>
     *
     * @param value 可选对象数组
     * @return 数组为空或长度为零时返回{@code true}
     */
    public static boolean isEmpty(Object[] value) {
        return CollectionUtils.isEmpty(value);
    }

    /**
     * 判断两个集合是否至少包含一个相同元素。
     *
     * <p>示例：{@code Func.containsAny(List.of("A", "B"), List.of("B", "C"))}返回{@code true}。</p>
     *
     * @param left 可选左集合
     * @param right 可选右集合
     * @return 两个非空集合存在交集时返回{@code true}
     */
    public static boolean containsAny(Collection<?> left, Collection<?> right) {
        return CollectionUtils.containsAny(left, right);
    }

    /**
     * 按首次出现顺序对集合元素去重。
     *
     * <p>示例：{@code Func.distinct(List.of("B", "A", "B"))}返回{@code ["B", "A"]}。</p>
     *
     * @param values 可选输入集合
     * @param <T> 元素类型
     * @return 保持首次出现顺序的不可变列表
     */
    public static <T> List<T> distinct(Collection<T> values) {
        return CollectionUtils.distinct(values);
    }

    /**
     * 使用指定格式输出本地日期时间。
     *
     * <p>示例：使用格式{@code uuuu/MM/dd HH:mm}可将{@code 2026-09-19T02:30}
     * 输出为{@code "2026/09/19 02:30"}。</p>
     *
     * @param value 待格式化时间
     * @param formatter 日期时间格式器
     * @return 格式化文本；时间为空时返回空
     */
    public static String formatDateTime(LocalDateTime value, DateTimeFormatter formatter) {
        return DateTimeUtils.format(value, formatter);
    }

    /**
     * 按照指定格式解析本地日期时间。
     *
     * <p>示例：使用格式{@code uuuu/MM/dd HH:mm}可将{@code "2026/09/19 02:30"}
     * 解析为{@code 2026-09-19T02:30}。</p>
     *
     * @param value 待解析文本
     * @param formatter 日期时间格式器
     * @return 解析后的时间；文本为空时返回空
     */
    public static LocalDateTime parseDateTime(String value, DateTimeFormatter formatter) {
        return DateTimeUtils.parse(value, formatter);
    }

    /**
     * 使用标准格式输出本地日期。
     *
     * <p>示例：{@code Func.formatDate(LocalDate.of(2026, 9, 19))}返回{@code "2026-09-19"}。</p>
     *
     * @param value 待格式化日期
     * @return {@code uuuu-MM-dd}格式文本；日期为空时返回空
     */
    public static String formatDate(LocalDate value) {
        return DateTimeUtils.formatDate(value);
    }

    /**
     * 使用标准格式输出本地日期时间。
     *
     * <p>示例：{@code Func.formatDateTime(LocalDateTime.of(2026, 9, 19, 2, 30))}
     * 返回{@code "2026-09-19 02:30:00"}。</p>
     *
     * @param value 待格式化日期时间
     * @return {@code uuuu-MM-dd HH:mm:ss}格式文本；时间为空时返回空
     */
    public static String formatDateTime(LocalDateTime value) {
        return DateTimeUtils.formatDateTime(value);
    }

    /**
     * 按照标准格式解析本地日期。
     *
     * <p>示例：{@code Func.parseDate("2026-09-19")}返回{@code 2026-09-19}。</p>
     *
     * @param value 待解析文本
     * @return 解析后的日期；文本为空时返回空
     */
    public static LocalDate parseDate(String value) {
        return DateTimeUtils.parseDate(value);
    }

    /**
     * 计算两个日期时间之间有方向的持续时间。
     *
     * <p>示例：从{@code 10:00}到{@code 11:30}返回{@code PT1H30M}。</p>
     *
     * @param start 起始时间
     * @param end 结束时间
     * @return 从起始时间到结束时间的持续时间
     */
    public static Duration durationBetween(LocalDateTime start, LocalDateTime end) {
        return DateTimeUtils.durationBetween(start, end);
    }

    /**
     * 生成标准UUID文本。
     *
     * <p>示例结果：{@code "123e4567-e89b-12d3-a456-426614174000"}；每次调用结果不同。</p>
     *
     * @return 包含连字符的36位小写UUID文本
     */
    public static String randomUuid() {
        return IdUtils.randomUuid();
    }

    /**
     * 生成简化UUID文本。
     *
     * <p>示例结果：{@code "123e4567e89b12d3a456426614174000"}；每次调用结果不同。</p>
     *
     * @return 不含连字符的32位小写UUID文本
     */
    public static String simpleUuid() {
        return IdUtils.simpleUuid();
    }

    /**
     * 计算两个非空数值之和。
     *
     * <p>示例：{@code Func.add(new BigDecimal("0.1"), new BigDecimal("0.2"))}返回{@code 0.3}。</p>
     *
     * @param left 左操作数
     * @param right 右操作数
     * @return 精确加法结果
     */
    public static BigDecimal add(BigDecimal left, BigDecimal right) {
        return ArithmeticUtils.add(left, right);
    }

    /**
     * 计算两个非空数值之差。
     *
     * <p>示例：{@code Func.subtract(new BigDecimal("10.5"), new BigDecimal("0.5"))}返回{@code 10.0}。</p>
     *
     * @param left 被减数
     * @param right 减数
     * @return 精确减法结果
     */
    public static BigDecimal subtract(BigDecimal left, BigDecimal right) {
        return ArithmeticUtils.subtract(left, right);
    }

    /**
     * 计算两个非空数值之积。
     *
     * <p>示例：{@code Func.multiply(new BigDecimal("2.5"), new BigDecimal("4"))}返回{@code 10.0}。</p>
     *
     * @param left 左操作数
     * @param right 右操作数
     * @return 精确乘法结果
     */
    public static BigDecimal multiply(BigDecimal left, BigDecimal right) {
        return ArithmeticUtils.multiply(left, right);
    }

    /**
     * 按指定精度和舍入规则计算两个数值之商。
     *
     * <p>示例：{@code Func.divide(BigDecimal.ONE, new BigDecimal("3"), 2, RoundingMode.HALF_UP)}
     * 返回{@code 0.33}。</p>
     *
     * @param dividend 被除数
     * @param divisor 除数
     * @param scale 小数位数
     * @param roundingMode 舍入规则
     * @return 除法结果
     */
    public static BigDecimal divide(
            BigDecimal dividend,
            BigDecimal divisor,
            int scale,
            RoundingMode roundingMode
    ) {
        return ArithmeticUtils.divide(dividend, divisor, scale, roundingMode);
    }

    /**
     * 按指定精度和舍入规则调整数值小数位。
     *
     * <p>示例：{@code Func.round(new BigDecimal("1.235"), 2, RoundingMode.HALF_UP)}返回{@code 1.24}。</p>
     *
     * @param value 待调整数值
     * @param scale 小数位数
     * @param roundingMode 舍入规则
     * @return 调整精度后的数值
     */
    public static BigDecimal round(BigDecimal value, int scale, RoundingMode roundingMode) {
        return ArithmeticUtils.round(value, scale, roundingMode);
    }

    /**
     * 将字节数组编码为标准Base64文本。
     *
     * <p>示例：{@code Func.encodeBase64(new byte[] {1, 2, 3})}返回{@code "AQID"}。</p>
     *
     * @param value 可选字节数组
     * @return Base64文本；输入为空时返回空
     */
    public static String encodeBase64(byte[] value) {
        return EncodingUtils.encodeBase64(value);
    }

    /**
     * 将UTF-8文本编码为标准Base64文本。
     *
     * <p>示例：{@code Func.encodeBase64Utf8("test")}返回{@code "dGVzdA=="}。</p>
     *
     * @param value 可选文本
     * @return Base64文本；输入为空时返回空
     */
    public static String encodeBase64Utf8(String value) {
        return EncodingUtils.encodeBase64Utf8(value);
    }

    /**
     * 解码标准Base64文本。
     *
     * <p>示例：{@code Func.decodeBase64("AQID")}返回字节{@code [1, 2, 3]}。</p>
     *
     * @param value 可选Base64文本
     * @return 解码后的字节数组；输入为空时返回空
     */
    public static byte[] decodeBase64(String value) {
        return EncodingUtils.decodeBase64(value);
    }

    /**
     * 从Unix或Windows风格路径中取得文件名。
     *
     * <p>示例：{@code Func.fileName("/tmp/report.pdf")}返回{@code "report.pdf"}。</p>
     *
     * @param value 可选路径或文件名
     * @return 最后一个路径片段；输入为空时返回空
     */
    public static String fileName(String value) {
        return FileNameUtils.name(value);
    }

    /**
     * 取得文件名的小写扩展名。
     *
     * <p>示例：{@code Func.fileExtension("report.PDF")}返回{@code "pdf"}。</p>
     *
     * @param value 可选路径或文件名
     * @return 不含点号的小写扩展名；没有扩展名时返回空字符串
     */
    public static String fileExtension(String value) {
        return FileNameUtils.extension(value);
    }

    /**
     * 取得不含最后一个扩展名的文件名。
     *
     * <p>示例：{@code Func.fileBaseName("report.final.pdf")}返回{@code "report.final"}。</p>
     *
     * @param value 可选路径或文件名
     * @return 不含路径和最后一个扩展名的文件名；输入为空时返回空
     */
    public static String fileBaseName(String value) {
        return FileNameUtils.baseName(value);
    }

    /**
     * 取得异常链最深处的异常。
     *
     * <p>示例：包装异常的原因为{@code IllegalStateException}时，本方法返回该原因实例。</p>
     *
     * @param throwable 可选异常
     * @return 根异常；输入为空时返回空
     */
    public static Throwable rootCause(Throwable throwable) {
        return ExceptionUtils.rootCause(throwable);
    }

    /**
     * 取得根异常的非空简短说明。
     *
     * <p>示例：根异常消息为{@code "连接超时"}时返回同一文本；不得直接用于未脱敏API响应。</p>
     *
     * @param throwable 可选异常
     * @return 根异常消息；输入为空时返回空字符串
     */
    public static String rootMessage(Throwable throwable) {
        return ExceptionUtils.rootMessage(throwable);
    }

    /**
     * 校验必填文本并返回裁剪后的值。
     *
     * <p>示例：{@code Func.requireText(" value ", "字段", 10)}返回{@code "value"}。</p>
     *
     * @param value 待校验文本
     * @param label 面向调用者的字段名称
     * @param maximumLength 允许的最大字符数，必须大于零
     * @return 非空且长度未超过限制的裁剪文本
     * @throws cn.zqkj.platform.common.exception.InvalidRequestException 文本为空、长度超限或长度限制无效时抛出
     */
    public static String requireText(String value, String label, int maximumLength) {
        return ValidationUtils.requireText(value, label, maximumLength);
    }
}
