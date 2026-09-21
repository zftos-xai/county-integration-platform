package cn.zqkj.platform.masterdata.domain.medicaldirectory.model;

import java.util.Arrays;

/**
 * 平台当前接入的药品、诊疗和耗材目录分类。
 *
 * <p>该分类是基础数据的业务范围，不暴露HIS协议枚举给平台API或持久化层。</p>
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
     * 创建平台目录分类。
     *
     * @param code 存储和来源适配使用的稳定分类编码
     * @param displayName 面向业务人员的名称
     */
    MedicalDirectoryType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * 返回稳定分类编码。
     *
     * @return 目录分类编码
     */
    public String code() {
        return code;
    }

    /**
     * 返回面向业务人员的名称。
     *
     * @return 目录名称
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 按稳定分类编码解析平台支持类型。
     *
     * @param code 分类编码
     * @return 平台目录分类
     */
    public static MedicalDirectoryType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的医疗目录类型：" + code));
    }
}
