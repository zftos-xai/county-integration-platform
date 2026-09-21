package cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo;

import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryType;

/**
 * 医院综合目录按类型统计的API输出。
 *
 * @param directoryType 医院综合目录类型
 * @param total 当前正式记录数
 */
public record HospitalDirectoryCountVO(HospitalDirectoryType directoryType, long total) {
}
