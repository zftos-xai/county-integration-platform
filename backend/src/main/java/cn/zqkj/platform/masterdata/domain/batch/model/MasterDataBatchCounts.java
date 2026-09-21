package cn.zqkj.platform.masterdata.domain.batch.model;

/**
 * 保存一次基础数据同步运行从来源取得到当前目录对账的数量事实。
 *
 * @param declared 来源数量接口声明数；来源不提供时为空
 * @param returned 实际取得记录数
 * @param duplicate 批次内重复记录数
 * @param invalid 必填项或代码不合法记录数
 * @param conflict 唯一标识对应事实冲突记录数
 * @param created 直接同步时新增记录数
 * @param updated 直接同步时更新或恢复有效的记录数
 * @param unchanged 与当前平台记录一致的记录数
 * @param sourceMissing 本次完整来源范围未返回、已标记无效的记录数
 * @param active 同步完成后的平台有效记录数；未完成时为空
 */
public record MasterDataBatchCounts(
        Long declared,
        long returned,
        long duplicate,
        long invalid,
        long conflict,
        long created,
        long updated,
        long unchanged,
        long sourceMissing,
        Long active
) {

    /**
     * 校验所有数量均为非负数。
     *
     * @param declared 来源数量接口声明数；来源不提供时为空
     * @param returned 实际取得记录数
     * @param duplicate 批次内重复记录数
     * @param invalid 必填项或代码不合法记录数
     * @param conflict 唯一标识对应事实冲突记录数
     * @param created 直接同步时新增记录数
     * @param updated 直接同步时更新或恢复有效的记录数
     * @param unchanged 与当前平台记录一致的记录数
     * @param sourceMissing 本次完整来源范围未返回、已标记无效的记录数
     * @param active 同步完成后的平台有效记录数；未完成时为空
     */
    public MasterDataBatchCounts {
        requireNonNegative(declared, "declared");
        requireNonNegative(returned, "returned");
        requireNonNegative(duplicate, "duplicate");
        requireNonNegative(invalid, "invalid");
        requireNonNegative(conflict, "conflict");
        requireNonNegative(created, "created");
        requireNonNegative(updated, "updated");
        requireNonNegative(unchanged, "unchanged");
        requireNonNegative(sourceMissing, "sourceMissing");
        requireNonNegative(active, "active");
    }

    /**
     * 校验可空数量没有出现负数。
     *
     * @param value 数量
     * @param field 字段名
     */
    private static void requireNonNegative(Long value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }
}
