package cn.zqkj.platform.masterdata.domain.icd10.model;

import java.util.Arrays;

/**
 * 平台公共ICD10目录的诊断类别。
 *
 * <p>该类别用于按来源返回口径分组、校验和展示；它不表示机构范围，也不表示来源是否提供诊断版本。</p>
 */
public enum Icd10DiagnosisCategory {

    /** 来源协议值0，对应西医诊断。 */
    WESTERN("0", "西医诊断"),
    /** 来源协议值1，对应中医诊断。 */
    TRADITIONAL("1", "中医诊断");

    private final String code;
    private final String displayName;

    /**
     * 创建公共诊断目录类别。
     *
     * @param code 来源协议和存储使用的稳定类别编码
     * @param displayName 面向业务人员的类别名称
     */
    Icd10DiagnosisCategory(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * 返回来源协议和存储使用的类别编码。
     *
     * @return 西医或中医诊断的稳定编码
     */
    public String code() {
        return code;
    }

    /**
     * 返回面向业务人员的类别名称。
     *
     * @return 西医诊断或中医诊断
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 按来源协议稳定编码解析诊断类别。
     *
     * @param code 来源返回或请求使用的类别编码
     * @return 对应的平台公共诊断类别
     * @throws IllegalArgumentException 编码不属于当前确认的西医或中医类别时抛出
     */
    public static Icd10DiagnosisCategory fromCode(String code) {
        return Arrays.stream(values())
                .filter(category -> category.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的ICD10诊断类别：" + code));
    }
}
