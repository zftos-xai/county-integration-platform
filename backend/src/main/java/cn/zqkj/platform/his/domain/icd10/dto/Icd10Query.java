package cn.zqkj.platform.his.domain.icd10.dto;

import cn.zqkj.platform.his.domain.icd10.model.Icd10DiagnosisCategory;
import java.time.LocalDateTime;

/**
 * 100-006 ICD10分页查询条件。
 *
 * <p>此对象只表达HIS协议请求，调用端点所属机构不会写入此对象，更不代表平台公共诊断目录的归属。</p>
 *
 * @param diseaseName 可选病种名称，最长20个字符
 * @param startRow 从一开始的起始行
 * @param endRow 不包含边界的结束行；TEST端点实测请求1-100返回99条
 * @param rangeStart 必填查询范围开始时间，按HIS本地钟面发送
 * @param rangeEnd 必填查询范围结束时间，按HIS本地钟面发送
 * @param diagnosisCategory 可选疾病类别；为空时请求来源默认的全部类别
 * @param diagnosisVersion 可选诊断版本；来源未提供时必须为空，不得由平台猜测
 */
public record Icd10Query(
        String diseaseName,
        long startRow,
        long endRow,
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd,
        Icd10DiagnosisCategory diagnosisCategory,
        String diagnosisVersion
) {
}
