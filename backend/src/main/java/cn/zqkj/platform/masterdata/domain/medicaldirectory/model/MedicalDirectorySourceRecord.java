package cn.zqkj.platform.masterdata.domain.medicaldirectory.model;

/**
 * 表示100-004分页取得的一条受控来源目录事实。
 *
 * @param type 目录类型
 * @param entry 已由HIS适配层转换的来源记录
 */
public record MedicalDirectorySourceRecord(MedicalDirectoryType type, MedicalDirectorySourceEntry entry) {

    /**
     * 创建来源目录事实。
     *
     * @param type 目录类型
     * @param entry 来源返回记录
     */
    public MedicalDirectorySourceRecord {
        if (type == null || entry == null) {
            throw new IllegalArgumentException("目录类型和来源记录不能为空");
        }
    }
}
