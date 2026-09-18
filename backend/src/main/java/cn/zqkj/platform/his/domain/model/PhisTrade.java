package cn.zqkj.platform.his.domain.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 定义基层HIS交易码、中文名称和业务分类的唯一目录。
 *
 * <p>带连字符的交易码以项目内《新版基层HIS与云平台V1.0接口文档》为主；文档未包含但外部
 * 平台代码提供的医防融合短码仅用于日志识别，不代表本平台已经确认其协议或允许调用。</p>
 */
public enum PhisTrade {

    INTERFACE_TEST("100-001", "接口测试", "基础数据"),
    LOGIN_VERIFICATION("100-002", "登录验证", "基础数据"),
    HOSPITAL_DIRECTORY_QUERY("100-003", "医院综合目录查询", "基础数据"),
    MEDICAL_DIRECTORY_QUERY("100-004", "医院三大目录查询", "基础数据"),
    MEDICAL_DIRECTORY_COUNT("100-005", "医院三大目录行数查询", "基础数据"),
    ICD10_QUERY("100-006", "ICD10数据查询", "基础数据"),
    ICD10_COUNT("100-007", "ICD10数据行数查询", "基础数据"),
    ORGANIZATION_QUERY("100-008", "医疗机构信息查询", "基础数据"),
    HIS_SINGLE_SIGN_ON("100-009", "HIS单点登录验证", "基础数据"),

    REGISTRATION_FEE_TYPE_QUERY("200-001", "获取挂号费用类型", "便民服务"),
    PAYMENT_ACCOUNT_QUERY("200-002", "获取机构收款账户", "便民服务"),
    REGISTRATION_TEMPLATE_QUERY("200-003", "查询机构挂号模板", "便民服务"),
    TEMPLATE_DEPARTMENT_QUERY("200-004", "查询挂号模板下的科室", "便民服务"),
    DEPARTMENT_DOCTOR_QUERY("200-005", "查询科室下的医生", "便民服务"),
    SCHEDULE_QUERY("200-006", "获取机构及人员排班信息", "便民服务"),
    APPOINTMENT_REGISTRATION("200-007", "预约挂号", "便民服务"),
    REGISTRATION_CANCELLATION("200-008", "撤销挂号", "便民服务"),
    REGISTRATION_RECORD_QUERY("200-009", "查询挂号记录", "便民服务"),
    OUTPATIENT_PENDING_PAYMENT_QUERY("200-010", "门诊待缴费清单查询", "便民服务"),
    OUTPATIENT_PAYMENT("200-011", "门诊缴费", "便民服务"),
    PAID_RECORD_QUERY("200-012", "已缴费列表查询", "便民服务"),
    PAYMENT_DETAIL_QUERY("200-013", "查询缴费清单明细", "便民服务"),
    OUTPATIENT_REFUND("200-014", "门诊退费", "便民服务"),
    TRANSACTION_BILL_QUERY("200-015", "交易账单数据查询", "便民服务"),
    OUTPATIENT_INFORMATION_QUERY("200-016", "门诊病人信息查询", "外部平台扩展"),

    INPATIENT_INFORMATION_QUERY("300-001", "住院病人信息查询", "双向转诊"),
    REFERRAL_OUTPATIENT_INFORMATION_QUERY("300-002", "门诊病人信息查询", "双向转诊"),
    REFERRAL_RECEIVE("300-003", "接收转诊", "双向转诊"),

    EMR_INPATIENT_INFORMATION_QUERY("400-001", "获取住院患者信息", "电子病历"),
    EMR_DIAGNOSIS_QUERY("400-002", "获取住院诊断", "电子病历"),
    EMR_WRITE_BACK("400-003", "回写电子病历", "电子病历"),
    EMR_HISTORY_QUERY("400-004", "获取HIS电子病历历史数据", "电子病历"),
    EMR_DOCTOR_QUERY("400-005", "获取电子病历医生数据", "电子病历"),
    EMR_LOGIN_VERIFICATION("400-006", "电子病历验证登录", "电子病历"),
    NEWBORN_DATA_QUERY("400-007", "获取新生儿数据", "电子病历"),
    TEMPERATURE_DATA_QUERY("400-008", "获取体温信息", "电子病历"),
    NURSING_DATA_QUERY("400-009", "获取护理信息", "电子病历"),
    SURGERY_DATA_QUERY("400-010", "获取手术信息", "电子病历"),

    PACS_ITEM_QUERY("500-001", "检查项目查询", "PACS"),
    PACS_APPLICATION_QUERY("500-002", "按申请单号获取检查申请单", "PACS"),
    // 接口总表和章节标题为500-003，章节参数误写500-004；确认前不登记冲突编码。
    PACS_REPORT_WRITE_BACK("500-003", "回写检查报告", "PACS"),
    LIS_ITEM_QUERY("600-001", "检验项目查询", "LIS"),
    LIS_APPLICATION_QUERY("600-002", "按申请单号获取检验申请单", "LIS"),
    LIS_REPORT_WRITE_BACK("600-003", "回写检验报告", "LIS"),
    ECG_ITEM_QUERY("700-001", "心电项目查询", "心电"),
    ECG_APPLICATION_QUERY("700-002", "按申请单号获取心电申请单", "心电"),
    // 接口总表和章节标题为700-003，章节参数误写700-004；确认前不登记冲突编码。
    ECG_REPORT_WRITE_BACK("700-003", "回写心电报告", "心电"),

    TRIAGE_WAITING_LIST_QUERY("800-001", "获取待接诊列表", "分诊叫号"),
    DISPENSING_LIST_QUERY("800-002", "获取门诊待发药及已发药列表", "分诊叫号"),
    EXAMINATION_EXECUTION_LIST_QUERY("800-003", "获取待执行检查检验列表", "分诊叫号"),
    PATIENT_CHECK_IN_WRITE_BACK("800-004", "接收患者签到状态回写", "分诊叫号"),
    REMOTE_CONSULTATION_WRITE_BACK("900-001", "远程会诊结果回写", "远程会诊"),

    HEALTH_MANAGEMENT_WRITE_BACK("800", "接收健康管理记录回写", "医防融合扩展"),
    HEALTH_CLINIC_STATUS_WRITE_BACK("801", "健康门诊管理状态回写", "医防融合扩展"),
    HIS_DEPARTMENT_TRANSFER("802", "HIS转科接收", "医防融合扩展"),
    LONG_PRESCRIPTION_QUERY("803", "按单次就诊获取长处方信息", "医防融合扩展"),
    VITAL_SIGNS_QUERY("804", "按单次就诊获取体征数据", "医防融合扩展"),
    OUTPATIENT_MEDICAL_RECORD_QUERY("805", "按单次就诊获取门诊病历", "医防融合扩展"),
    SUSPECTED_OUTPATIENT_QUERY("806", "可疑门诊患者列表", "医防融合扩展"),
    CHECK_IN_HIGHLIGHT_WRITE_BACK("807", "签到状态回写", "医防融合扩展");

    private static final Map<String, PhisTrade> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(PhisTrade::code, Function.identity()));

    private final String code;
    private final String displayName;
    private final String category;

    /**
     * 创建交易定义。
     *
     * @param code 稳定交易码
     * @param displayName 面向运维人员的中文名称
     * @param category 业务分类
     */
    PhisTrade(String code, String displayName, String category) {
        this.code = code;
        this.displayName = displayName;
        this.category = category;
    }

    /** @return 稳定交易码 */
    public String code() {
        return code;
    }

    /** @return 面向运维人员的中文名称 */
    public String displayName() {
        return displayName;
    }

    /** @return 业务分类 */
    public String category() {
        return category;
    }

    /**
     * 根据交易码查找已登记定义。
     *
     * @param code 待查找交易码
     * @return 已登记定义；未登记或空值时为空
     */
    public static Optional<PhisTrade> findByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE.get(code.trim()));
    }
}
