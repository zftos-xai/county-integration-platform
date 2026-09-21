package cn.zqkj.platform.framework.database;

/**
 * 描述数据库契约中的一个物理字段。
 *
 * @param schemaName 数据库架构名称
 * @param tableName 表名
 * @param columnName 字段名
 * @param typeName 规范化后的SQL Server类型名
 * @param maxLength 最大字节数；非字符或二进制字段为空
 * @param precision 数值精度；不适用时为空
 * @param scale 数值或时间小数位；不适用时为空
 * @param nullable 是否允许空值
 * @param identity 是否为自增字段
 * @param computed 是否为计算字段
 */
public record DatabaseContractColumn(
        String schemaName,
        String tableName,
        String columnName,
        String typeName,
        Integer maxLength,
        Integer precision,
        Integer scale,
        boolean nullable,
        boolean identity,
        boolean computed
) {

    /**
     * 返回用于契约索引和差异报告的稳定字段全名。
     *
     * @return 架构、表和字段组成的全名
     */
    public String qualifiedName() {
        return schemaName + "." + tableName + "." + columnName;
    }

    /**
     * 返回便于运维人员阅读的字段结构摘要。
     *
     * @return SQL类型、长度、精度和空值规则摘要
     */
    public String describe() {
        StringBuilder description = new StringBuilder(typeName);
        if (maxLength != null) {
            int displayedLength = typeName.startsWith("n") && maxLength > 0 ? maxLength / 2 : maxLength;
            description.append('(').append(displayedLength == -1 ? "max" : displayedLength).append(')');
        } else if (precision != null) {
            description.append('(').append(precision);
            if (scale != null) {
                description.append(',').append(scale);
            }
            description.append(')');
        } else if (scale != null) {
            description.append('(').append(scale).append(')');
        }
        description.append(nullable ? " NULL" : " NOT NULL");
        if (identity) {
            description.append(" IDENTITY");
        }
        if (computed) {
            description.append(" COMPUTED");
        }
        return description.toString();
    }
}
