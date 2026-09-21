package cn.zqkj.platform.his.domain.model;

import java.util.List;

/**
 * 基层HIS医院综合目录条目。
 *
 * <p>医生目录会补充科室、执业和人员信息；其他目录不适用的字段为空。</p>
 *
 * @param directoryCode 目录编码
 * @param directoryName 目录名称
 * @param mnemonicCode 助记码
 * @param categoryName 目录类别名称
 * @param remark 备注；床位目录的真实返回值为所在病区编码
 * @param departmentCode 科室编码
 * @param departmentName 科室名称
 * @param ward 病区编码；医生目录的真实返回值可用该字段形成医生与病区关系
 * @param organizationCode 机构编码
 * @param nationalInsuranceCode 国家医保编码
 * @param userAccount 用户账号
 * @param practicingCertificateCode 执业证书编码
 * @param qualificationCertificateCode 资格证书编码
 * @param identityNumber 身份证号
 * @param practitionerCategory 医执人员类别
 * @param staffType 医护人员类型
 * @param photo 照片地址或标识
 * @param introduction 简介
 * @param roles 医生角色列表；真实HIS返回结构与接口文档的单个字符串描述不一致
 * @param titleCode 职称编码
 * @param titleName 职称名称
 * @param contactPhone 联系电话
 * @param gender 性别
 */
public record HospitalDirectoryEntry(
        String directoryCode,
        String directoryName,
        String mnemonicCode,
        String categoryName,
        String remark,
        String departmentCode,
        String departmentName,
        String ward,
        String organizationCode,
        String nationalInsuranceCode,
        String userAccount,
        String practicingCertificateCode,
        String qualificationCertificateCode,
        String identityNumber,
        String practitionerCategory,
        String staffType,
        String photo,
        String introduction,
        List<HospitalDirectoryRole> roles,
        String titleCode,
        String titleName,
        String contactPhone,
        String gender
) {
}
