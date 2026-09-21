package cn.zqkj.platform.his.domain.dto;

import cn.zqkj.platform.his.domain.model.MedicalDirectoryType;

import java.time.LocalDateTime;

/**
 * 100-005医院三大目录声明行数查询条件。
 *
 * @param directoryType 必填目录类型
 * @param directoryName 可选目录名称
 * @param rangeStart 与100-004完全一致的约定范围开始时间
 * @param rangeEnd 与100-004完全一致的约定范围结束时间
 * @param sourceOrganizationCode HIS机构参数
 */
public record MedicalDirectoryCountQuery(
        MedicalDirectoryType directoryType,
        String directoryName,
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd,
        String sourceOrganizationCode
) {
}
