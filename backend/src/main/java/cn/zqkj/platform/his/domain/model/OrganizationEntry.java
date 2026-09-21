package cn.zqkj.platform.his.domain.model;

/**
 * 表示100-008返回的一条来源医疗机构资料。
 *
 * @param sourceOrganizationId 供其他HIS接口作为机构编码使用的来源机构ID
 * @param hospitalName 医院名称
 * @param address 地址
 * @param contactPhone 联系电话
 * @param postalCode 邮政编码
 * @param contactPerson 联系人
 */
public record OrganizationEntry(
        String sourceOrganizationId,
        String hospitalName,
        String address,
        String contactPhone,
        String postalCode,
        String contactPerson
) {
}
