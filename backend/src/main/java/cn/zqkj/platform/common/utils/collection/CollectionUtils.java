package cn.zqkj.platform.common.utils.collection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 实现集合、映射和数组的通用状态判断。
 *
 * <p>本类参考RuoYi通用集合判断方式，是{@code Func}门面的分类实现，业务代码不得绕过门面直接调用。</p>
 */
public final class CollectionUtils {

    /**
     * 禁止创建集合工具实现实例。
     */
    private CollectionUtils() {
    }

    /**
     * 判断集合是否为空。
     *
     * <p>示例：{@code isEmpty(List.of())}返回{@code true}。</p>
     *
     * @param value 可选集合
     * @return 集合为空或没有元素时返回{@code true}
     */
    public static boolean isEmpty(Collection<?> value) {
        return value == null || value.isEmpty();
    }

    /**
     * 判断映射是否为空。
     *
     * <p>示例：{@code isEmpty(Map.of())}返回{@code true}。</p>
     *
     * @param value 可选映射
     * @return 映射为空或没有条目时返回{@code true}
     */
    public static boolean isEmpty(Map<?, ?> value) {
        return value == null || value.isEmpty();
    }

    /**
     * 判断对象数组是否为空。
     *
     * <p>示例：{@code isEmpty(new Object[0])}返回{@code true}。</p>
     *
     * @param value 可选对象数组
     * @return 数组为空或长度为零时返回{@code true}
     */
    public static boolean isEmpty(Object[] value) {
        return value == null || value.length == 0;
    }

    /**
     * 判断两个集合是否至少包含一个相同元素。
     *
     * <p>示例：{@code containsAny(List.of("A", "B"), List.of("B"))}返回{@code true}。</p>
     *
     * @param left 可选左集合
     * @param right 可选右集合
     * @return 两个非空集合存在交集时返回{@code true}
     */
    public static boolean containsAny(Collection<?> left, Collection<?> right) {
        if (isEmpty(left) || isEmpty(right)) {
            return false;
        }
        Collection<?> smaller = left.size() <= right.size() ? left : right;
        Collection<?> larger = smaller == left ? right : left;
        for (Object value : smaller) {
            if (larger.contains(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按首次出现顺序对集合元素去重。
     *
     * <p>示例：{@code distinct(List.of("B", "A", "B"))}返回{@code ["B", "A"]}。</p>
     *
     * @param values 可选输入集合
     * @param <T> 元素类型
     * @return 保持首次出现顺序的不可变列表；输入为空时返回空列表
     */
    public static <T> List<T> distinct(Collection<T> values) {
        return isEmpty(values) ? List.of() : List.copyOf(new LinkedHashSet<>(values));
    }
}
