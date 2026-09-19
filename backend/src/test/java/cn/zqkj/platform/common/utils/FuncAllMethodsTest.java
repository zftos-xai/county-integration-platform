package cn.zqkj.platform.common.utils;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 逐项验证{@link Func}公开的全部工具方法，并通过签名清单防止新增方法遗漏测试。
 */
class FuncAllMethodsTest {

    /** 验证全部日期时间方法的正常值、空值和显式格式行为。 */
    @Test
    void coversEveryDateTimeMethod() {
        OffsetDateTime source = OffsetDateTime.of(2026, 9, 19, 10, 30, 0, 0, ZoneOffset.ofHours(8));
        LocalDateTime utc = LocalDateTime.of(2026, 9, 19, 2, 30);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm");

        assertEquals(utc, Func.toUtc(source));
        assertNull(Func.toUtc(null));
        assertEquals(utc.atOffset(ZoneOffset.UTC), Func.toOffset(utc));
        assertNull(Func.toOffset(null));
        assertEquals("2026/09/19 02:30", Func.formatDateTime(utc, formatter));
        assertEquals(utc, Func.parseDateTime("2026/09/19 02:30", formatter));
        assertEquals("2026-09-19", Func.formatDate(LocalDate.of(2026, 9, 19)));
        assertEquals("2026-09-19 02:30:00", Func.formatDateTime(utc));
        assertEquals(LocalDate.of(2026, 9, 19), Func.parseDate("2026-09-19"));
        assertEquals(Duration.ofMinutes(90), Func.durationBetween(utc, utc.plusMinutes(90)));
    }

    /** 验证全部文本方法的转换结果和主要边界行为。 */
    @Test
    void coversEveryTextMethod() {
        assertEquals("value", Func.trimToNull(" value "));
        assertNull(Func.trimToNull(" "));
        assertTrue(Func.isBlank(" \t"));
        assertTrue(Func.isNotBlank(" value "));
        assertEquals("ab**ef", Func.mask("abcdef", 2, 4));
        assertEquals("http_server_url", Func.toSnakeCase("HTTPServerUrl"));
        assertEquals("userLoginName", Func.toCamelCase("USER_LOGIN_NAME"));
        assertEquals("cde", Func.substring("abcdef", -4, -1));
        assertEquals("批次 B-1", Func.format("批次 {}", "B-1"));
        assertEquals(List.of("A", "B"), Func.split(" A, ,B ", ","));
        assertEquals("0007", Func.padLeft("7", 4, '0'));
        assertThrows(IllegalArgumentException.class, () -> Func.mask("abc", -1, 2));
    }

    /** 验证集合重载、交集判断和顺序去重方法。 */
    @Test
    void coversEveryCollectionMethod() {
        assertTrue(Func.isEmpty((Collection<?>) null));
        assertFalse(Func.isEmpty(List.of("A")));
        assertTrue(Func.isEmpty(Map.of()));
        assertTrue(Func.isEmpty(new Object[0]));
        assertTrue(Func.containsAny(List.of("A", "B"), List.of("B", "C")));
        assertFalse(Func.containsAny(List.of("A"), List.of("B")));
        assertEquals(List.of("B", "A"), Func.distinct(List.of("B", "A", "B")));
    }

    /** 验证标识符与全部精确数值方法。 */
    @Test
    void coversEveryIdentifierAndArithmeticMethod() {
        String randomUuid = Func.randomUuid();
        String simpleUuid = Func.simpleUuid();

        assertEquals(randomUuid, UUID.fromString(randomUuid).toString());
        assertEquals(32, simpleUuid.length());
        assertFalse(simpleUuid.contains("-"));
        assertEquals(new BigDecimal("0.3"), Func.add(new BigDecimal("0.1"), new BigDecimal("0.2")));
        assertEquals(new BigDecimal("10.0"), Func.subtract(new BigDecimal("10.5"), new BigDecimal("0.5")));
        assertEquals(new BigDecimal("10.0"), Func.multiply(new BigDecimal("2.5"), new BigDecimal("4")));
        assertEquals(new BigDecimal("0.33"), Func.divide(
                BigDecimal.ONE, new BigDecimal("3"), 2, RoundingMode.HALF_UP
        ));
        assertEquals(new BigDecimal("1.24"), Func.round(new BigDecimal("1.235"), 2, RoundingMode.HALF_UP));
    }

    /** 验证编码、文件名、异常分析和请求文本校验方法。 */
    @Test
    void coversEveryLowFrequencyAndValidationMethod() {
        byte[] bytes = {1, 2, 3};
        String utf8 = Func.encodeBase64Utf8("基础数据");
        IllegalStateException root = new IllegalStateException("root");
        RuntimeException wrapper = new RuntimeException("wrapper", root);

        assertEquals("AQID", Func.encodeBase64(bytes));
        assertArrayEquals(bytes, Func.decodeBase64("AQID"));
        assertEquals("基础数据", new String(Func.decodeBase64(utf8), StandardCharsets.UTF_8));
        assertEquals("report.final.PDF", Func.fileName("C:\\tmp\\report.final.PDF"));
        assertEquals("pdf", Func.fileExtension("C:\\tmp\\report.final.PDF"));
        assertEquals("report.final", Func.fileBaseName("C:\\tmp\\report.final.PDF"));
        assertSame(root, Func.rootCause(wrapper));
        assertEquals("root", Func.rootMessage(wrapper));
        assertEquals("value", Func.requireText(" value ", "字段", 5));
        assertThrows(InvalidRequestException.class, () -> Func.requireText(" ", "字段", 5));
    }

    /** 验证公开静态方法清单与本测试覆盖的契约清单完全一致。 */
    @Test
    void keepsPublicMethodInventoryExplicit() {
        Set<String> expected = Set.of(
                "add(BigDecimal,BigDecimal)",
                "containsAny(Collection,Collection)",
                "decodeBase64(String)",
                "distinct(Collection)",
                "divide(BigDecimal,BigDecimal,int,RoundingMode)",
                "durationBetween(LocalDateTime,LocalDateTime)",
                "encodeBase64(byte[])",
                "encodeBase64Utf8(String)",
                "fileBaseName(String)",
                "fileExtension(String)",
                "fileName(String)",
                "format(String,Object[])",
                "formatDate(LocalDate)",
                "formatDateTime(LocalDateTime)",
                "formatDateTime(LocalDateTime,DateTimeFormatter)",
                "isBlank(CharSequence)",
                "isEmpty(Collection)",
                "isEmpty(Map)",
                "isEmpty(Object[])",
                "isNotBlank(CharSequence)",
                "mask(CharSequence,int,int)",
                "multiply(BigDecimal,BigDecimal)",
                "padLeft(String,int,char)",
                "parseDate(String)",
                "parseDateTime(String,DateTimeFormatter)",
                "randomUuid()",
                "requireText(String,String,int)",
                "rootCause(Throwable)",
                "rootMessage(Throwable)",
                "round(BigDecimal,int,RoundingMode)",
                "simpleUuid()",
                "split(String,String)",
                "substring(String,int,int)",
                "subtract(BigDecimal,BigDecimal)",
                "toCamelCase(String)",
                "toOffset(LocalDateTime)",
                "toSnakeCase(String)",
                "toUtc(OffsetDateTime)",
                "trimToNull(String)"
        );
        Set<String> actual = Arrays.stream(Func.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()))
                .map(FuncAllMethodsTest::signature)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(expected, actual);
    }

    /**
     * 将反射方法转换为稳定的简短签名。
     *
     * @param method 公开工具方法
     * @return 方法名和擦除后的参数类型列表
     */
    private static String signature(Method method) {
        String parameters = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
        return method.getName() + "(" + parameters + ")";
    }
}
