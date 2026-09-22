package cn.zqkj.platform.common.utils.validation;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.utils.encoding.EncodingUtils;
import cn.zqkj.platform.common.utils.text.TextUtils;

/**
 * 实现跨业务输入值的基础合法性校验。
 *
 * <p>本类是{@code Func}门面的分类实现，业务代码不得绕过门面直接调用。</p>
 */
public final class ValidationUtils {

    /**
     * 禁止创建校验工具实现实例。
     */
    private ValidationUtils() {
    }

    /**
     * 将客户端版本转换为可用于 SQL Server 条件更新的八字节值。
     *
     * @param value Base64 行版本
     * @return 八字节行版本
     * @throws InvalidRequestException 版本缺失、编码错误或长度不正确时抛出
     */
    public static byte[] decodeRowVersion(String value) {
        try {
            byte[] version = EncodingUtils.decodeBase64(value);
            if (version == null || version.length != Long.BYTES) {
                throw new InvalidRequestException("version 必须表示一个 8 字节的 SQL Server 行版本号");
            }
            return version;
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("version 必须是有效的 Base64 文本");
        }
    }

    /**
     * 校验必填文本并返回规范化结果。
     *
     * <p>示例：{@code requireText(" value ", "字段", 10)}返回{@code "value"}。</p>
     *
     * @param value 待校验文本
     * @param label 面向调用者的字段名称
     * @param maximumLength 允许的最大字符数，必须大于零
     * @return 非空且长度未超过限制的裁剪文本
     * @throws InvalidRequestException 文本为空、长度超限或长度限制无效时抛出
     */
    public static String requireText(String value, String label, int maximumLength) {
        if (maximumLength < 1) {
            throw new InvalidRequestException(label + "长度限制不符合要求");
        }
        String normalized = TextUtils.trimToNull(value);
        if (normalized == null || normalized.length() > maximumLength) {
            throw new InvalidRequestException(label + "不符合要求");
        }
        return normalized;
    }
}
