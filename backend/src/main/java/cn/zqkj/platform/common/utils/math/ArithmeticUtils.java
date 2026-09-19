package cn.zqkj.platform.common.utils.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 实现基于{@link BigDecimal}的通用精确数值运算。
 *
 * <p>本类参考RuoYi精确运算工具，并改为直接接收{@link BigDecimal}以避免二进制浮点误差；
 * 是{@code Func}门面的分类实现，业务代码不得绕过门面直接调用。</p>
 */
public final class ArithmeticUtils {

    /**
     * 禁止创建数值运算工具实现实例。
     */
    private ArithmeticUtils() {
    }

    /**
     * 计算两个非空数值之和。
     *
     * <p>示例：{@code add(new BigDecimal("0.1"), new BigDecimal("0.2"))}返回{@code 0.3}。</p>
     *
     * @param left 左操作数
     * @param right 右操作数
     * @return 精确加法结果
     */
    public static BigDecimal add(BigDecimal left, BigDecimal right) {
        return requireNumber(left, "左操作数").add(requireNumber(right, "右操作数"));
    }

    /**
     * 计算两个非空数值之差。
     *
     * <p>示例：{@code subtract(new BigDecimal("10.5"), new BigDecimal("0.5"))}返回{@code 10.0}。</p>
     *
     * @param left 被减数
     * @param right 减数
     * @return 精确减法结果
     */
    public static BigDecimal subtract(BigDecimal left, BigDecimal right) {
        return requireNumber(left, "被减数").subtract(requireNumber(right, "减数"));
    }

    /**
     * 计算两个非空数值之积。
     *
     * <p>示例：{@code multiply(new BigDecimal("2.5"), new BigDecimal("4"))}返回{@code 10.0}。</p>
     *
     * @param left 左操作数
     * @param right 右操作数
     * @return 精确乘法结果
     */
    public static BigDecimal multiply(BigDecimal left, BigDecimal right) {
        return requireNumber(left, "左操作数").multiply(requireNumber(right, "右操作数"));
    }

    /**
     * 按指定精度和舍入规则计算两个数值之商。
     *
     * <p>示例：{@code divide(BigDecimal.ONE, new BigDecimal("3"), 2, RoundingMode.HALF_UP)}
     * 返回{@code 0.33}。</p>
     *
     * @param dividend 被除数
     * @param divisor 除数，不得为零
     * @param scale 小数位数，不得小于零
     * @param roundingMode 舍入规则
     * @return 除法结果
     */
    public static BigDecimal divide(
            BigDecimal dividend,
            BigDecimal divisor,
            int scale,
            RoundingMode roundingMode
    ) {
        BigDecimal checkedDivisor = requireNumber(divisor, "除数");
        if (checkedDivisor.signum() == 0) {
            throw new ArithmeticException("除数不能为零");
        }
        if (scale < 0) {
            throw new IllegalArgumentException("小数位数不能小于零");
        }
        if (roundingMode == null) {
            throw new IllegalArgumentException("舍入规则不能为空");
        }
        return requireNumber(dividend, "被除数").divide(checkedDivisor, scale, roundingMode);
    }

    /**
     * 按指定精度和舍入规则调整数值小数位。
     *
     * <p>示例：{@code round(new BigDecimal("1.235"), 2, RoundingMode.HALF_UP)}返回{@code 1.24}。</p>
     *
     * @param value 待调整数值
     * @param scale 小数位数，不得小于零
     * @param roundingMode 舍入规则
     * @return 调整精度后的数值
     */
    public static BigDecimal round(BigDecimal value, int scale, RoundingMode roundingMode) {
        if (scale < 0) {
            throw new IllegalArgumentException("小数位数不能小于零");
        }
        if (roundingMode == null) {
            throw new IllegalArgumentException("舍入规则不能为空");
        }
        return requireNumber(value, "待调整数值").setScale(scale, roundingMode);
    }

    /**
     * 校验数值操作数不为空。
     *
     * @param value 待校验数值
     * @param label 操作数名称
     * @return 原数值
     */
    private static BigDecimal requireNumber(BigDecimal value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value;
    }
}
