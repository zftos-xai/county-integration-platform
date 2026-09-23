package cn.zqkj.platform.masterdata.domain.icd10.vo;

import java.util.List;

/**
 * 一个公共ICD10批次的HIS调用事实分页响应。
 *
 * <p>该对象不对应独立表；业务说明：聚合调用记录页和总数，供详情页稳定翻页，
 * 不代表同步结果或管理审计的汇总。</p>
 *
 * @param items 本页调用事实
 * @param total 当前批次的调用事实总数
 * @param page 当前一开始页码
 * @param pageSize 每页大小
 */
public record Icd10HisInvocationPageVO(List<Icd10HisInvocationVO> items, long total, int page, int pageSize) {
}
