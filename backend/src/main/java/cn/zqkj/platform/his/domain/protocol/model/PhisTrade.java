package cn.zqkj.platform.his.domain.protocol.model;

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

    /** 接口测试交易。 */
    INTERFACE_TEST("100-001", "接口测试", "基础数据"),
    /** 登录验证交易。 */
    LOGIN_VERIFICATION("100-002", "登录验证", "基础数据"),
    /** 医院综合目录查询交易。 */
    HOSPITAL_DIRECTORY_QUERY("100-003", "医院综合目录查询", "基础数据"),
    /** 医院三大目录查询交易。 */
    MEDICAL_DIRECTORY_QUERY("100-004", "医院三大目录查询", "基础数据"),
    /** 医院三大目录行数查询交易。 */
    MEDICAL_DIRECTORY_COUNT("100-005", "医院三大目录行数查询", "基础数据"),
    /** ICD10数据查询交易。 */
    ICD10_QUERY("100-006", "ICD10数据查询", "基础数据"),
    /** ICD10数据行数查询交易。 */
    ICD10_COUNT("100-007", "ICD10数据行数查询", "基础数据"),
    /** 医疗机构信息查询交易。 */
    ORGANIZATION_QUERY("100-008", "医疗机构信息查询", "基础数据"),
    /** HIS单点登录验证交易。 */
    HIS_SINGLE_SIGN_ON("100-009", "HIS单点登录验证", "基础数据"),

    /** 获取挂号费用类型交易。 */
    REGISTRATION_FEE_TYPE_QUERY("200-001", "获取挂号费用类型", "便民服务"),
    /** 获取机构收款账户交易。 */
    PAYMENT_ACCOUNT_QUERY("200-002", "获取机构收款账户", "便民服务"),
    /** 查询机构挂号模板交易。 */
    REGISTRATION_TEMPLATE_QUERY("200-003", "查询机构挂号模板", "便民服务"),
    /** 查询挂号模板下的科室交易。 */
    TEMPLATE_DEPARTMENT_QUERY("200-004", "查询挂号模板下的科室", "便民服务"),
    /** 查询科室下的医生交易。 */
    DEPARTMENT_DOCTOR_QUERY("200-005", "查询科室下的医生", "便民服务"),
    /** 获取机构及人员排班信息交易。 */
    SCHEDULE_QUERY("200-006", "获取机构及人员排班信息", "便民服务"),
    /** 预约挂号交易。 */
    APPOINTMENT_REGISTRATION("200-007", "预约挂号", "便民服务"),
    /** 撤销挂号交易。 */
    REGISTRATION_CANCELLATION("200-008", "撤销挂号", "便民服务"),
    /** 查询挂号记录交易。 */
    REGISTRATION_RECORD_QUERY("200-009", "查询挂号记录", "便民服务"),
    /** 门诊待缴费清单查询交易。 */
    OUTPATIENT_PENDING_PAYMENT_QUERY("200-010", "门诊待缴费清单查询", "便民服务"),
    /** 门诊缴费交易。 */
    OUTPATIENT_PAYMENT("200-011", "门诊缴费", "便民服务"),
    /** 已缴费列表查询交易。 */
    PAID_RECORD_QUERY("200-012", "已缴费列表查询", "便民服务"),
    /** 查询缴费清单明细交易。 */
    PAYMENT_DETAIL_QUERY("200-013", "查询缴费清单明细", "便民服务"),
    /** 门诊退费交易。 */
    OUTPATIENT_REFUND("200-014", "门诊退费", "便民服务"),
    /** 交易账单数据查询交易。 */
    TRANSACTION_BILL_QUERY("200-015", "交易账单数据查询", "便民服务"),
    /** 门诊病人信息查询交易。 */
    OUTPATIENT_INFORMATION_QUERY("200-016", "门诊病人信息查询", "外部平台扩展"),

    /** 住院病人信息查询交易。 */
    INPATIENT_INFORMATION_QUERY("300-001", "住院病人信息查询", "双向转诊"),
    /** 门诊病人信息查询交易。 */
    REFERRAL_OUTPATIENT_INFORMATION_QUERY("300-002", "门诊病人信息查询", "双向转诊"),
    /** 接收转诊交易。 */
    REFERRAL_RECEIVE("300-003", "接收转诊", "双向转诊"),

    /** 获取住院患者信息交易。 */
    EMR_INPATIENT_INFORMATION_QUERY("400-001", "获取住院患者信息", "电子病历"),
    /** 获取住院诊断交易。 */
    EMR_DIAGNOSIS_QUERY("400-002", "获取住院诊断", "电子病历"),
    /** 回写电子病历交易。 */
    EMR_WRITE_BACK("400-003", "回写电子病历", "电子病历"),
    /** 获取HIS电子病历历史数据交易。 */
    EMR_HISTORY_QUERY("400-004", "获取HIS电子病历历史数据", "电子病历"),
    /** 获取电子病历医生数据交易。 */
    EMR_DOCTOR_QUERY("400-005", "获取电子病历医生数据", "电子病历"),
    /** 电子病历验证登录交易。 */
    EMR_LOGIN_VERIFICATION("400-006", "电子病历验证登录", "电子病历"),
    /** 获取新生儿数据交易。 */
    NEWBORN_DATA_QUERY("400-007", "获取新生儿数据", "电子病历"),
    /** 获取体温信息交易。 */
    TEMPERATURE_DATA_QUERY("400-008", "获取体温信息", "电子病历"),
    /** 获取护理信息交易。 */
    NURSING_DATA_QUERY("400-009", "获取护理信息", "电子病历"),
    /** 获取手术信息交易。 */
    SURGERY_DATA_QUERY("400-010", "获取手术信息", "电子病历"),

    /** 检查项目查询交易。 */
    PACS_ITEM_QUERY("500-001", "检查项目查询", "PACS"),
    /** 按申请单号获取检查申请单交易。 */
    PACS_APPLICATION_QUERY("500-002", "按申请单号获取检查申请单", "PACS"),
    // 接口总表和章节标题为500-003，章节参数误写500-004；确认前不登记冲突编码。
    /** 回写检查报告交易。 */
    PACS_REPORT_WRITE_BACK("500-003", "回写检查报告", "PACS"),
    /** 检验项目查询交易。 */
    LIS_ITEM_QUERY("600-001", "检验项目查询", "LIS"),
    /** 按申请单号获取检验申请单交易。 */
    LIS_APPLICATION_QUERY("600-002", "按申请单号获取检验申请单", "LIS"),
    /** 回写检验报告交易。 */
    LIS_REPORT_WRITE_BACK("600-003", "回写检验报告", "LIS"),
    /** 心电项目查询交易。 */
    ECG_ITEM_QUERY("700-001", "心电项目查询", "心电"),
    /** 按申请单号获取心电申请单交易。 */
    ECG_APPLICATION_QUERY("700-002", "按申请单号获取心电申请单", "心电"),
    // 接口总表和章节标题为700-003，章节参数误写700-004；确认前不登记冲突编码。
    /** 回写心电报告交易。 */
    ECG_REPORT_WRITE_BACK("700-003", "回写心电报告", "心电"),

    /** 获取待接诊列表交易。 */
    TRIAGE_WAITING_LIST_QUERY("800-001", "获取待接诊列表", "分诊叫号"),
    /** 获取门诊待发药及已发药列表交易。 */
    DISPENSING_LIST_QUERY("800-002", "获取门诊待发药及已发药列表", "分诊叫号"),
    /** 获取待执行检查检验列表交易。 */
    EXAMINATION_EXECUTION_LIST_QUERY("800-003", "获取待执行检查检验列表", "分诊叫号"),
    /** 接收患者签到状态回写交易。 */
    PATIENT_CHECK_IN_WRITE_BACK("800-004", "接收患者签到状态回写", "分诊叫号"),
    /** 远程会诊结果回写交易。 */
    REMOTE_CONSULTATION_WRITE_BACK("900-001", "远程会诊结果回写", "远程会诊"),

    /** 接收健康管理记录回写交易。 */
    HEALTH_MANAGEMENT_WRITE_BACK("800", "接收健康管理记录回写", "医防融合扩展"),
    /** 健康门诊管理状态回写交易。 */
    HEALTH_CLINIC_STATUS_WRITE_BACK("801", "健康门诊管理状态回写", "医防融合扩展"),
    /** HIS转科接收交易。 */
    HIS_DEPARTMENT_TRANSFER("802", "HIS转科接收", "医防融合扩展"),
    /** 按单次就诊获取长处方信息交易。 */
    LONG_PRESCRIPTION_QUERY("803", "按单次就诊获取长处方信息", "医防融合扩展"),
    /** 按单次就诊获取体征数据交易。 */
    VITAL_SIGNS_QUERY("804", "按单次就诊获取体征数据", "医防融合扩展"),
    /** 按单次就诊获取门诊病历交易。 */
    OUTPATIENT_MEDICAL_RECORD_QUERY("805", "按单次就诊获取门诊病历", "医防融合扩展"),
    /** 可疑门诊患者列表交易。 */
    SUSPECTED_OUTPATIENT_QUERY("806", "可疑门诊患者列表", "医防融合扩展"),
    /** 签到状态回写交易。 */
    CHECK_IN_HIGHLIGHT_WRITE_BACK("807", "签到状态回写", "医防融合扩展");

    private static final Map<String, PhisTrade> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(PhisTrade::code, Function.identity()));

    private final String code;
    private final String displayName;
    private final String category;
    private final String description;
    private final boolean documentedInPublicSpecification;

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
        this.documentedInPublicSpecification = !"外部平台扩展".equals(category)
                && !"医防融合扩展".equals(category) && !"200-016".equals(code);
        this.description = buildDescription(code, documentedInPublicSpecification);
    }

    /**
     * 返回协议或领域定义的稳定代码。
     *
     * @return 稳定交易码
     */
    public String code() {
        return code;
    }

    /**
     * 返回面向业务人员的显示名称。
     *
     * @return 面向运维人员的中文名称
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 返回交易所属业务分类。
     *
     * @return 业务分类
     */
    public String category() {
        return category;
    }

    /**
     * 返回管理端使用的接口说明；未收录交易明确说明其文档状态。
     *
     * @return 基于交易目录名称及公版文档收录状态的说明
     */
    public String description() {
        return description;
    }

    /**
     * 判断交易是否出现在项目内公版V1.0接口文档总表。
     *
     * @return 出现在公版接口总表时为true
     */
    public boolean documentedInPublicSpecification() {
        return documentedInPublicSpecification;
    }

    private static String buildDescription(String code, boolean documented) {
        if (!documented) {
            return "公版接口文档未收录此交易；名称来自平台现有交易目录，接口定义与调用约束待确认。";
        }
        String description = switch (code) {
            case "100-001" -> "检测接口客户端与HIS系统中心服务器是否连通。";
            case "100-002" -> "验证医院用户登录医保报账客户端的安全性；用户名和密码由HIS系统统一分配。";
            case "100-003" -> "获取HIS科室、医师、病区和床位基本信息。";
            case "100-004" -> "获取HIS药品、诊疗和耗材三大目录基本信息。";
            case "100-005" -> "获取HIS药品、诊疗和耗材三大目录行数。";
            case "100-006" -> "获取HIS系统ICD-10基本信息。";
            case "100-007" -> "获取HIS系统ICD-10数据行数。";
            case "100-008" -> "获取HIS医疗机构详细信息。";
            case "100-009" -> "执行HIS单点登录验证。";
            case "200-001" -> "获取HIS挂号费用类型列表。";
            case "200-002" -> "获取HIS机构支付方式列表。";
            case "200-003" -> "获取HIS挂号模板。";
            case "200-004" -> "获取HIS挂号模板下的科室。";
            case "200-006" -> "获取HIS机构及人员排班信息。";
            case "200-007" -> "向HIS保存挂号信息。";
            case "200-008" -> "在HIS中为厂商撤销挂号。";
            case "200-009" -> "获取HIS挂号记录。";
            case "200-010" -> "获取HIS门诊费用清单。";
            case "200-011" -> "执行HIS门诊收费。";
            case "200-013" -> "获取HIS缴费清单明细。";
            case "200-014" -> "退还通过APP或微信公众号收取的费用，文档说明为整笔退款。";
            case "200-015" -> "获取基于接口交易的账单清单详细信息。";
            case "300-001" -> "获取HIS住院病人基本信息。";
            case "300-003" -> "接收双向转诊下转信息。";
            case "400-001" -> "获取HIS住院病人信息。";
            case "400-002" -> "获取HIS住院病人诊断信息。";
            case "400-003" -> "向HIS回写病历数据。";
            case "400-004" -> "获取HIS电子病历历史数据。";
            case "400-005" -> "获取电子病历医生数据。";
            case "400-006" -> "执行电子病历验证登录。";
            case "400-008" -> "获取电子病历体温单。";
            case "400-009" -> "获取电子病历护理单。";
            case "400-010" -> "获取HIS手术信息。";
            case "500-001", "600-001", "700-001" -> "获取指定机构下LIS和PACS检查/检验包及明细清单；公版文档对分类名称表述不一致，具体交易以交易码为准。";
            case "500-002" -> "公版章节说明存在内容冲突（标题为按申请单号获取申请单，接口说明写为FHIR检查报告推送），需按HIS确认版本核实。";
            case "500-003" -> "公版总表登记为回写报告；正文参数编号存在冲突，调用参数须以确认后的HIS版本为准。";
            case "600-003" -> "回写检验报告；公版正文未提供更具体的接口说明。";
            case "700-002" -> "通过申请单号获取申请单。";
            case "700-003" -> "公版总表登记为回写心电报告；正文参数编号存在冲突，调用参数须以确认后的HIS版本为准。";
            case "800-001" -> "获取HIS待接诊记录。";
            case "800-002" -> "供药房排队叫号使用，获取门诊待发药及最近7天已发药列表。";
            case "800-003" -> "获取待执行的检查检验列表。";
            case "800-004" -> "患者签到时向HIS回写签到信息。";
            case "900-001" -> "执行远程会诊结果回写。";
            default -> "公版接口总表收录此交易；正文未提供可确认的接口说明，请以对应HIS版本核实。";
        };
        return description;
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
