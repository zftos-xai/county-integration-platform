package cn.zqkj.platform.his.domain.lis.model;

/**
 * 表示600-001返回的一条检验项目包明细。
 *
 * <p>金额和数量按来源 JSON 标量文本保留。公版表格声明其为字符串，但示例使用数值，
 * 因此平台不在协议边界擅自改变精度或格式。</p>
 *
 * @param packageName 来源项目包名称
 * @param packageId 来源项目包标识
 * @param packageCode 来源项目包编码
 * @param packageType 来源项目包类型
 * @param packageTotalAmount 来源项目包金额文本
 * @param itemName 来源明细名称
 * @param itemId 来源明细标识
 * @param itemUnitPrice 来源明细单价文本
 * @param itemQuantity 来源明细数量文本
 * @param itemUnit 来源明细单位
 * @param organizationName 来源机构名称
 * @param organizationId 来源机构标识
 */
public record LisItemEntry(
        String packageName,
        String packageId,
        String packageCode,
        String packageType,
        String packageTotalAmount,
        String itemName,
        String itemId,
        String itemUnitPrice,
        String itemQuantity,
        String itemUnit,
        String organizationName,
        String organizationId
) {
}
