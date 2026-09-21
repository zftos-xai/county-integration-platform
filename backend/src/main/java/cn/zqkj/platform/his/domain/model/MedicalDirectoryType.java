package cn.zqkj.platform.his.domain.model;

import java.util.Arrays;

/**
 * 定义100-004和100-005已经确认需要接入的医院目录类型。
 *
 * <p>疫苗和血液虽然出现在上游接口文档中，但尚未进入当前业务范围，因此不在此枚举开放。</p>
 */
public enum MedicalDirectoryType {

    /** 中药目录。 */
    TRADITIONAL_MEDICINE("0", "中药"),
    /** 西药目录。 */
    WESTERN_MEDICINE("1", "西药"),
    /** 诊疗目录。 */
    TREATMENT("2", "诊疗"),
    /** 耗材目录。 */
    CONSUMABLE("3", "耗材");

    private final String code;
    private final String displayName;

    /**
     * 使用HIS约定编码和业务名称创建医疗目录类型常量。
     *
     * @param code HIS目录类型编码
     * @param displayName 面向业务人员的名称
     */
    MedicalDirectoryType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * 返回协议或领域定义的稳定代码。
     *
     * @return HIS目录类型编码
     */
    public String code() {
        return code;
    }

    /**
     * 返回面向业务人员的显示名称。
     *
     * @return 面向业务人员的名称
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 按HIS目录类型编码解析受支持的领域枚举。
     *
     * @param code HIS目录类型编码
     * @return 对应目录类型
     */
    public static MedicalDirectoryType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的医院目录类型：" + code));
    }
}
