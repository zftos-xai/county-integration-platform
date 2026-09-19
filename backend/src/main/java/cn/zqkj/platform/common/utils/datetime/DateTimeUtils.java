package cn.zqkj.platform.common.utils.datetime;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * 实现跨业务的日期时间转换与规范化处理。
 *
 * <p>本类是{@code Func}门面的分类实现，业务代码不得绕过门面直接调用。</p>
 */
public final class DateTimeUtils {

    /** 标准本地日期格式。 */
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    /** 标准本地日期时间格式。 */
    public static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    /**
     * 禁止创建日期时间工具实现实例。
     */
    private DateTimeUtils() {
    }

    /**
     * 将带偏移量的时间转换为UTC本地时间。
     *
     * <p>示例：{@code 2026-09-19T10:30+08:00}转换为{@code 2026-09-19T02:30}。</p>
     *
     * @param value 可选带偏移量时间
     * @return UTC本地时间；输入为空时返回空
     */
    public static LocalDateTime toUtc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * 为UTC本地时间附加UTC偏移量。
     *
     * <p>示例：{@code 2026-09-19T02:30}转换为{@code 2026-09-19T02:30Z}。</p>
     *
     * @param value 可选UTC本地时间
     * @return 带UTC偏移量的时间；输入为空时返回空
     */
    public static OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    /**
     * 使用指定格式输出本地日期时间。
     *
     * <p>示例：格式{@code uuuu/MM/dd HH:mm}输出{@code "2026/09/19 02:30"}。</p>
     *
     * @param value 待格式化时间
     * @param formatter 非空格式器
     * @return 格式化文本；时间为空时返回空
     */
    public static String format(LocalDateTime value, DateTimeFormatter formatter) {
        if (value == null) {
            return null;
        }
        if (formatter == null) {
            throw new IllegalArgumentException("日期时间格式器不能为空");
        }
        return formatter.format(value);
    }

    /**
     * 按照指定格式解析本地日期时间。
     *
     * <p>示例：按格式{@code uuuu/MM/dd HH:mm}解析{@code "2026/09/19 02:30"}。</p>
     *
     * @param value 待解析文本
     * @param formatter 非空格式器
     * @return 解析后的本地日期时间；文本为空时返回空
     * @throws DateTimeParseException 文本与格式不匹配时抛出
     */
    public static LocalDateTime parse(String value, DateTimeFormatter formatter) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (formatter == null) {
            throw new IllegalArgumentException("日期时间格式器不能为空");
        }
        return LocalDateTime.parse(value.trim(), formatter);
    }

    /**
     * 使用标准格式输出本地日期。
     *
     * <p>示例：{@code 2026-09-19}输出为{@code "2026-09-19"}。</p>
     *
     * @param value 待格式化日期
     * @return {@code uuuu-MM-dd}格式文本；日期为空时返回空
     */
    public static String formatDate(LocalDate value) {
        return value == null ? null : DATE_FORMATTER.format(value);
    }

    /**
     * 使用标准格式输出本地日期时间。
     *
     * <p>示例：{@code 2026-09-19T02:30}输出为{@code "2026-09-19 02:30:00"}。</p>
     *
     * @param value 待格式化日期时间
     * @return {@code uuuu-MM-dd HH:mm:ss}格式文本；时间为空时返回空
     */
    public static String formatDateTime(LocalDateTime value) {
        return format(value, DATE_TIME_FORMATTER);
    }

    /**
     * 按照标准格式解析本地日期。
     *
     * <p>示例：{@code "2026-09-19"}解析为{@code 2026-09-19}。</p>
     *
     * @param value 待解析文本
     * @return 解析后的日期；文本为空时返回空
     * @throws DateTimeParseException 文本不符合{@code uuuu-MM-dd}格式时抛出
     */
    public static LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value.trim(), DATE_FORMATTER);
    }

    /**
     * 计算两个日期时间之间有方向的持续时间。
     *
     * <p>示例：从{@code 10:00}到{@code 11:30}返回{@code PT1H30M}。</p>
     *
     * @param start 起始时间
     * @param end 结束时间
     * @return 从起始时间到结束时间的持续时间，结束早于起始时为负值
     */
    public static Duration durationBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("起始时间和结束时间不能为空");
        }
        return Duration.between(start, end);
    }
}
