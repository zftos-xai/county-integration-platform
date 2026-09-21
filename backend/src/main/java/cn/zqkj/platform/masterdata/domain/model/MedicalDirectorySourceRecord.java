package cn.zqkj.platform.masterdata.domain.model;

import cn.zqkj.platform.his.domain.model.MedicalDirectoryEntry;
import cn.zqkj.platform.his.domain.model.MedicalDirectoryType;

/**
 * 表示100-004分页取得的一条受控来源目录事实。
 *
 * @param type 目录类型
 * @param entry 来源返回记录
 */
public record MedicalDirectorySourceRecord(MedicalDirectoryType type, MedicalDirectoryEntry entry) {

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
