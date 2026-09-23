package cn.zqkj.platform.exchange.domain.vo;

/**
 * 管理端读取的HIS交易目录契约，不暴露枚举常量名等内部实现信息。
 *
 * @param code 稳定交易码
 * @param displayName 中文交易名称
 * @param category 业务分类
 * @param description 接口说明及文档状态提示
 * @param documentedInPublicSpecification 是否收录于公版接口文档总表
 */
public record PhisTradeVO(
        String code,
        String displayName,
        String category,
        String description,
        boolean documentedInPublicSpecification
) {
}
