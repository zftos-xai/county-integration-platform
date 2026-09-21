package cn.zqkj.platform.masterdata.domain.medicaldirectory.model;

/**
 * 表示来源接口约定的一段分页范围：行号从1开始，起止位置均包含在内。
 *
 * @param startRow 起始行，从一开始
 * @param endRow 结束行，包含该行
 */
public record SourcePage(long startRow, long endRow) {

    /**
     * 创建受控来源分页范围。
     *
     * @param startRow 起始行，从一开始
     * @param endRow 结束行，包含该行
     * @throws IllegalArgumentException 起始行小于1或结束行早于起始行时抛出
     */
    public SourcePage {
        if (startRow < 1 || endRow < startRow) {
            throw new IllegalArgumentException("来源分页范围无效");
        }
    }
}
