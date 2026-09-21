package cn.zqkj.platform.masterdata.domain.hospitaldirectory.model;

/**
 * 平台医院综合目录的业务分类。
 *
 * <p>编码与当前HIS 100-003交易参数一致，但本枚举属于平台基础数据域；
 * HIS协议枚举不得出现在平台的Mapper、API输出或持久化模型中。</p>
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
     * 创建平台目录分类。
     *
     * @param code 存储和来源适配使用的稳定分类编码
     */
    HospitalDirectoryType(int code) {
        this.code = code;
    }

    /**
     * 返回稳定分类编码。
     *
     * @return 目录分类编码
     */
    public int code() {
        return code;
    }

    /**
     * 为MyBatis参数绑定提供JavaBean式编码访问器。
     *
     * @return 目录分类编码
     */
    public int getCode() {
        return code;
    }
}
