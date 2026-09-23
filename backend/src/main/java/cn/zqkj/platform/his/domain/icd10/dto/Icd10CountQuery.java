package cn.zqkj.platform.his.domain.icd10.dto;

import cn.zqkj.platform.his.domain.icd10.model.Icd10DiagnosisCategory;
import java.time.LocalDateTime;

/**
 * 100-007 ICD10声明行数查询条件。
 *
 * <p>取得数据前应使用与100-006完全相同的名称、时间、类别和版本条件，避免把不同范围的数量当作完整性证据。</p>
 *
 * @param diseaseName 可选病种名称，最长20个字符
 * @param rangeStart 必填查询范围开始时间，按HIS本地钟面发送
 * @param rangeEnd 必填查询范围结束时间，按HIS本地钟面发送
 * @param diagnosisCategory 可选疾病类别；为空时请求来源默认的全部类别
 * @param diagnosisVersion 可选诊断版本；来源未提供时必须为空，不得由平台猜测
 */
public record Icd10CountQuery(
        String diseaseName,
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd,
        Icd10DiagnosisCategory diagnosisCategory,
        String diagnosisVersion
) {
}
