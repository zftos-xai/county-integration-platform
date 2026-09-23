package cn.zqkj.platform.masterdata.domain.icd10.vo;

import java.util.List;

/**
 * 公共ICD10目录分页查询API输出。
 * @param items 当前页目录记录
 * @param counts 按类别汇总的当前数量
 * @param total 当前筛选总数
 * @param page 当前页码
 * @param pageSize 页大小
 */
public record Icd10DirectoryPageVO(List<Icd10DirectoryItemVO> items, List<Icd10DirectoryCountVO> counts,
                                   long total, int page, int pageSize) { }
