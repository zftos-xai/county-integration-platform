package cn.zqkj.platform.his.domain.dto;

import cn.zqkj.platform.his.domain.model.HospitalDirectoryType;

/**
 * 医院综合目录查询条件。
 *
 * @param directoryType 必填的目录类型
 * @param directoryName 可选的目录名称，最多20个字符
 * @param sourceOrganizationCode 可选的基层HIS机构编码，最多50个字符
 */
public record HospitalDirectoryQuery(
        HospitalDirectoryType directoryType,
        String directoryName,
        String sourceOrganizationCode
) {
}
