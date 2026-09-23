package cn.zqkj.platform.masterdata.domain.icd10.model;

/**
 * 表示100-006分页取得的一条公共ICD10来源事实。
 *
 * <p>类别来自本次请求条件而非机构上下文。经原始响应全量复核，来源疾病ID在每个类别内非空且唯一，
 * 因而优先作为跨批次幂等身份；当未来来源没有提供疾病ID时，才回退到疾病编码。</p>
 *
 * @param category 已明确请求的诊断类别
 * @param entry 已由HIS适配层转换的来源条目
 */
public record Icd10SourceRecord(Icd10DiagnosisCategory category, Icd10SourceEntry entry) {

    /**
     * 创建受控ICD10来源事实。
     *
     * @param category 已明确请求的诊断类别
     * @param entry 来源返回记录
     */
    public Icd10SourceRecord {
        if (category == null || entry == null) {
            throw new IllegalArgumentException("诊断类别和来源记录不能为空");
        }
    }

    /**
     * 返回持久化和批内校验共用的来源稳定身份。
     *
     * <p>前缀避免疾病ID与疾病编码取值相同而碰撞。来源疾病ID缺失时无法证明更细粒度身份，
     * 因此保守回退到疾病编码，以阻止同编码的互相覆盖。</p>
     *
     * @return 以 {@code ID:} 或 {@code CODE:} 标识的来源稳定身份
     */
    public String sourceRecordKey() {
        return entry.sourceDiseaseId() == null ? "CODE:" + entry.diseaseCode() : "ID:" + entry.sourceDiseaseId();
    }
}
