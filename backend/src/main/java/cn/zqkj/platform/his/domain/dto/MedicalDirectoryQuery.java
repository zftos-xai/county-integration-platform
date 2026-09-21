package cn.zqkj.platform.his.domain.dto;

import cn.zqkj.platform.his.domain.model.MedicalDirectoryType;

import java.time.LocalDateTime;

/**
 * 100-004医院三大目录分页查询条件。
 *
 * @param directoryType 必填目录类型
 * @param directoryName 可选目录名称
 * @param startRow 从一开始的起始行
 * @param endRow 包含边界的结束行
 * @param rangeStart 约定查询范围开始时间
 * @param rangeEnd 约定查询范围结束时间
 * @param sourceOrganizationCode HIS机构参数
 */
public record MedicalDirectoryQuery(
        MedicalDirectoryType directoryType,
        String directoryName,
        long startRow,
        long endRow,
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd,
        String sourceOrganizationCode
) {
}
