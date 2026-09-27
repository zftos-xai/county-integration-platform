package cn.zqkj.platform.his.domain.lis.model;

/**
 * 表示600-002返回的一条LIS申请及其患者、就诊和执行信息。
 *
 * <p>响应包含敏感患者信息，只允许在本次受控业务调用内传递；不得写入交换记录、普通日志或缓存。
 * 未在公版资料中解释的缩写字段保留其来源名称，不推断业务含义。</p>
 *
 * @param billId HIS申请记录标识（BILLID）
 * @param otherType 来源其他类型值（OTHERTYPE）
 * @param csType 来源CS类型值（CSTYPE）
 * @param itemCode 检验项目编码（ITEMCODE）
 * @param itemName 检验项目名称（ITEMNAME）
 * @param fee 来源费用标量文本（FEE）
 * @param personId 患者来源标识（PERSONID）
 * @param personName 患者姓名（PERSONNAME）
 * @param createTime 来源创建时间文本（CREATETIME）
 * @param operatorId 开单操作人标识（OPERATERID）
 * @param operatorName 开单操作人姓名（OPERATERNAME）
 * @param organizationId 来源机构标识（ORGID）
 * @param organizationName 来源机构名称（ORGNAME）
 * @param diagnosis 诊断文本（DIAGNOSIS）
 * @param bwInfo 来源BWINFO字段原值（BWINFO）
 * @param businessId 来源业务标识（BUSINESSID）
 * @param visitCode 来源就诊编码（JZCODE）
 * @param sourceType 来源业务类型值（SOURCETYPE）
 * @param sourceTypeName 来源业务类型名称（SOURCETYPENAME）
 * @param urgency 来源紧急标识（URGENCY）
 * @param operatorDepartmentId 开单科室标识（OPERATERDEPTID）
 * @param operatorDepartmentName 开单科室名称（OPERATERDEPTNAME）
 * @param currentBedId 当前床位来源标识（CURRENTBEDID）
 * @param bedCode 床位编码（BEDCODE）
 * @param bedName 床位名称（BEDNAME）
 * @param cardType 证件类型来源值（CARDTYPE）
 * @param cardTypeName 证件类型名称（CARDTYPENAME）
 * @param cardId 证件号码（CARDID）
 * @param telephone 来源联系电话（TEL）
 * @param gender 性别来源值（GENDER）
 * @param birthday 出生日期来源文本（BIRTHDAY）
 * @param executionDepartmentId 执行科室标识（ACTDEPTID）
 * @param executionDepartmentName 执行科室名称（ACTDEPTNAME）
 */
public record LisApplicationEntry(
        String billId,
        String otherType,
        String csType,
        String itemCode,
        String itemName,
        String fee,
        String personId,
        String personName,
        String createTime,
        String operatorId,
        String operatorName,
        String organizationId,
        String organizationName,
        String diagnosis,
        String bwInfo,
        String businessId,
        String visitCode,
        String sourceType,
        String sourceTypeName,
        String urgency,
        String operatorDepartmentId,
        String operatorDepartmentName,
        String currentBedId,
        String bedCode,
        String bedName,
        String cardType,
        String cardTypeName,
        String cardId,
        String telephone,
        String gender,
        String birthday,
        String executionDepartmentId,
        String executionDepartmentName
) {
}
