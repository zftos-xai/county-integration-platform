package cn.zqkj.platform.common.utils.encoding;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 实现基于JDK标准库的通用文本与二进制编码。
 *
 * <p>本类是{@code Func}门面的分类实现，业务代码不得绕过门面直接调用。</p>
 */
public final class EncodingUtils {

    /**
     * 禁止创建编码工具实现实例。
     */
    private EncodingUtils() {
    }

    /**
     * 将字节数组编码为标准Base64文本。
     *
     * <p>示例：{@code encodeBase64(new byte[] {1, 2, 3})}返回{@code "AQID"}。</p>
     *
     * @param value 可选字节数组
     * @return Base64文本；输入为空时返回空
     */
    public static String encodeBase64(byte[] value) {
        return value == null ? null : Base64.getEncoder().encodeToString(value);
    }

    /**
     * 将UTF-8文本编码为标准Base64文本。
     *
     * <p>示例：{@code encodeBase64Utf8("test")}返回{@code "dGVzdA=="}。</p>
     *
     * @param value 可选文本
     * @return Base64文本；输入为空时返回空
     */
    public static String encodeBase64Utf8(String value) {
        return value == null ? null : encodeBase64(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码标准Base64文本。
     *
     * <p>示例：{@code decodeBase64("AQID")}返回字节{@code [1, 2, 3]}。</p>
     *
     * @param value 可选Base64文本
     * @return 解码后的字节数组；输入为空时返回空
     * @throws IllegalArgumentException 输入不是有效Base64文本时抛出
     */
    public static byte[] decodeBase64(String value) {
        return value == null ? null : Base64.getDecoder().decode(value);
    }
}
