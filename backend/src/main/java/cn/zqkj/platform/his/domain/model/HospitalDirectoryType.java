package cn.zqkj.platform.his.domain.model;

/**
 * 医院综合目录类型，对应基层HIS交易100-003的目录类型参数。
 */
public enum HospitalDirectoryType {

    /** 科室目录。 */
    DEPARTMENT(0),
    /** 医生目录。 */
    DOCTOR(1),
    /** 病区目录。 */
    WARD(2),
    /** 床位目录。 */
    BED(3);

    private final int code;

    /**
     * 使用HIS约定编码创建医院综合目录类型常量。
     *
     * @param code 基层HIS约定的目录类型编码
     */
    HospitalDirectoryType(int code) {
        this.code = code;
    }

    /**
     * 返回协议或领域定义的稳定代码。
     *
     * @return 基层HIS约定的目录类型编码
     */
    public int code() {
        return code;
    }

    /**
     * 为MyBatis嵌套属性绑定提供JavaBean式目录类型编码访问器。
     *
     * @return 基层HIS目录类型编码
     */
    public int getCode() {
        return code;
    }
}
