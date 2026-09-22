package cn.zqkj.platform.common.utils;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证公共工具门面保持时间、文本和校验行为稳定。
 */
class FuncTest {

    /** 行版本解码只接受八字节Base64值，防止格式错误进入业务用例。 */
    @Test
    void validatesRowVersionBeforeUse() {
        Assertions.assertArrayEquals(new byte[8], Func.decodeRowVersion("AAAAAAAAAAA="));
        for (String value : new String[]{null, "", "bad!", "AQ=="}) {
            Assertions.assertThrows(
                    InvalidRequestException.class,
                    () -> Func.decodeRowVersion(value));
        }
    }

    /** 验证带偏移量的时间按同一时刻转换为UTC本地时间。 */
    @Test
    void convertsOffsetDateTimeToUtcLocalDateTime() {
        OffsetDateTime source = OffsetDateTime.of(2026, 9, 19, 10, 30, 0, 0, ZoneOffset.ofHours(8));

        assertEquals(LocalDateTime.of(2026, 9, 19, 2, 30), Func.toUtc(source));
        assertNull(Func.toUtc(null));
    }

    /** 验证UTC本地时间转换后明确携带UTC偏移量。 */
    @Test
    void attachesUtcOffsetToLocalDateTime() {
        LocalDateTime source = LocalDateTime.of(2026, 9, 19, 2, 30);

        assertEquals(OffsetDateTime.of(source, ZoneOffset.UTC), Func.toOffset(source));
        assertNull(Func.toOffset(null));
    }

    /** 验证文本裁剪并把空白内容统一转换为空值。 */
    @Test
    void trimsTextAndNormalizesBlankToNull() {
        assertEquals("value", Func.trimToNull("  value  "));
        assertNull(Func.trimToNull("  "));
        assertNull(Func.trimToNull(null));
    }

    /** 验证可选文本的字段长度检查使用裁剪后的值，并拒绝非法上限。 */
    @Test
    void checksTrimmedTextLength() {
        assertFalse(Func.exceedsTrimmedLength(null, 3));
        assertFalse(Func.exceedsTrimmedLength("   ", 3));
        assertFalse(Func.exceedsTrimmedLength(" abc ", 3));
        assertTrue(Func.exceedsTrimmedLength(" abcd ", 3));
        assertThrows(IllegalArgumentException.class, () -> Func.exceedsTrimmedLength("a", -1));
    }

    /** 验证必填文本校验返回裁剪值并拒绝空白或超长内容。 */
    @Test
    void requiresTextWithinMaximumLength() {
        assertEquals("value", Func.requireText(" value ", "字段", 5));
        assertThrows(InvalidRequestException.class, () -> Func.requireText(" ", "字段", 5));
        assertThrows(InvalidRequestException.class, () -> Func.requireText("123456", "字段", 5));
        assertThrows(InvalidRequestException.class, () -> Func.requireText("value", "字段", 0));
    }

    /** 验证从RuoYi筛选改写的文本辅助方法保持明确的空值和边界行为。 */
    @Test
    void handlesGeneralTextOperations() {
        assertTrue(Func.isBlank(" \t"));
        assertTrue(Func.isNotBlank(" value "));
        assertEquals("ab**ef", Func.mask("abcdef", 2, 4));
        assertEquals("http_server_url", Func.toSnakeCase("HTTPServerUrl"));
        assertEquals("sha256_hash", Func.toSnakeCase("sha256Hash"));
        assertEquals("httpServerUrl", Func.toCamelCase("HTTP_SERVER_URL"));
        assertEquals("cde", Func.substring("abcdef", -4, -1));
        assertEquals("批次 B-1 已创建", Func.format("批次 {} 已创建", "B-1"));
        assertEquals("保留 {}，替换 B-1", Func.format("保留 \\{}，替换 {}", "B-1"));
        assertEquals(List.of("A", "B"), Func.split(" A, ,B ", ","));
        assertEquals("0007", Func.padLeft("7", 4, '0'));
    }

    /** 验证集合判断、交集检查和顺序去重均为空值安全。 */
    @Test
    void handlesGeneralCollectionOperations() {
        assertTrue(Func.isEmpty((List<?>) null));
        assertTrue(Func.isEmpty(Map.of()));
        assertTrue(Func.isEmpty(new Object[0]));
        assertFalse(Func.containsAny(List.of("A"), List.of("B")));
        assertTrue(Func.containsAny(List.of("A", "B"), List.of("B", "C")));
        assertEquals(List.of("B", "A"), Func.distinct(List.of("B", "A", "B")));
    }

    /** 验证日期格式、解析和持续时间计算使用稳定的显式规则。 */
    @Test
    void handlesGeneralDateTimeOperations() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 19, 2, 30);

        assertEquals("2026-09-19", Func.formatDate(LocalDate.of(2026, 9, 19)));
        assertEquals("2026-09-19 02:30:00", Func.formatDateTime(start));
        assertEquals(LocalDate.of(2026, 9, 19), Func.parseDate("2026-09-19"));
        assertThrows(DateTimeParseException.class, () -> Func.parseDate("2026-02-30"));
        assertEquals(Duration.ofMinutes(90), Func.durationBetween(start, start.plusMinutes(90)));
    }

    /** 验证精确数值运算显式处理精度、舍入和非法除数。 */
    @Test
    void handlesPreciseArithmetic() {
        assertEquals(new BigDecimal("0.3"), Func.add(new BigDecimal("0.1"), new BigDecimal("0.2")));
        assertEquals(new BigDecimal("0.33"), Func.divide(
                BigDecimal.ONE, new BigDecimal("3"), 2, RoundingMode.HALF_UP
        ));
        assertEquals(new BigDecimal("1.24"), Func.round(new BigDecimal("1.235"), 2, RoundingMode.HALF_UP));
        assertThrows(ArithmeticException.class, () -> Func.divide(
                BigDecimal.ONE, BigDecimal.ZERO, 2, RoundingMode.HALF_UP
        ));
    }

    /** 验证编码、文件名和异常链等小众辅助能力不依赖外部库。 */
    @Test
    void handlesLowFrequencyUtilityOperations() {
        String encoded = Func.encodeBase64Utf8("基础数据");
        assertEquals("基础数据", new String(Func.decodeBase64(encoded), StandardCharsets.UTF_8));
        assertEquals("report.final.PDF", Func.fileName("C:\\tmp\\report.final.PDF"));
        assertEquals("pdf", Func.fileExtension("C:\\tmp\\report.final.PDF"));
        assertEquals("report.final", Func.fileBaseName("C:\\tmp\\report.final.PDF"));

        IllegalStateException root = new IllegalStateException("root");
        RuntimeException wrapper = new RuntimeException("wrapper", root);
        assertSame(root, Func.rootCause(wrapper));
        assertEquals("root", Func.rootMessage(wrapper));
        assertEquals(32, Func.simpleUuid().length());
        assertEquals(36, Func.randomUuid().length());
    }
}
