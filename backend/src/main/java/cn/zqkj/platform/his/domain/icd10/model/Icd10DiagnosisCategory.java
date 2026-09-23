package cn.zqkj.platform.his.domain.icd10.model;

/**
 * 定义100-006和100-007可选的ICD10疾病类别。
 *
 * <p>空值表示来源协议默认的全部类别；该枚举不表达目录归属，也不代表来源是否提供诊断版本。</p>
 */
public enum Icd10DiagnosisCategory {

    /** HIS协议值0，对应西医诊断。 */
    WESTERN("0", "西医诊断"),
    /** HIS协议值1，对应中医诊断。 */
    TRADITIONAL("1", "中医诊断");

    private final String code;
    private final String displayName;

    /**
     * 创建来源协议疾病类别。
     *
     * @param code HIS请求字段“疾病类别”的稳定值
     * @param displayName 面向业务人员的名称
     */
    Icd10DiagnosisCategory(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * 返回写入HIS请求的疾病类别代码。
     *
     * @return 西医或中医的来源协议代码
     */
    public String code() {
        return code;
    }

    /**
     * 返回疾病类别的业务显示名称。
     *
     * @return 不带机构归属语义的诊断类别名称
     */
    public String displayName() {
        return displayName;
    }
}
