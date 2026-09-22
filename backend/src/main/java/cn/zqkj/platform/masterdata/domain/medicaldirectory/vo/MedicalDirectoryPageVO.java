package cn.zqkj.platform.masterdata.domain.medicaldirectory.vo;

import java.util.List;

/**
 * 医疗目录分页查询的API输出。
 *
 * @param items 当前页有效目录
 * @param counts 当前查询机构范围内的四类目录数量
 * @param total 当前筛选总数
 * @param page 当前页码
 * @param pageSize 页大小
 */
public record MedicalDirectoryPageVO(
        List<MedicalDirectoryItemVO> items,
        List<MedicalDirectoryCountVO> counts,
        long total,
        int page,
        int pageSize
) {
}
