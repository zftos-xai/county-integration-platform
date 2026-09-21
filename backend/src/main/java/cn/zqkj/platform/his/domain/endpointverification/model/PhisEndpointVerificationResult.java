package cn.zqkj.platform.his.domain.endpointverification.model;

/**
 * 表达基层HIS端点自动校验的可确认结论。
 *
 * <p>该结果不保存认证信息或上游原始报文。通过时携带100-008确认的来源机构；拒绝时只携带
 * 可展示的受控原因。通信和协议结果未知仍由对应异常表达，不能伪装为明确拒绝。</p>
 *
 * @param sourceOrganizationId 已确认的HIS来源机构编码；拒绝时为空
 * @param sourceOrganizationName 已确认的HIS来源机构名称；拒绝时为空
 * @param failureSummary 明确拒绝的安全摘要；通过时为空
 */
public record PhisEndpointVerificationResult(
        String sourceOrganizationId,
        String sourceOrganizationName,
        String failureSummary
) {

    /**
     * 构造100-008和100-003均通过后的确认结果。
     *
     * @param sourceOrganizationId HIS来源机构编码
     * @param sourceOrganizationName HIS来源机构名称
     * @return 可用于启用端点的确认结果
     */
    public static PhisEndpointVerificationResult verified(
            String sourceOrganizationId,
            String sourceOrganizationName
    ) {
        return new PhisEndpointVerificationResult(sourceOrganizationId, sourceOrganizationName, null);
    }

    /**
     * 构造HIS已明确拒绝或无法唯一确认来源机构的结果。
     *
     * @param failureSummary 面向配置管理员的安全摘要
     * @return 不允许启用端点的明确拒绝结果
     */
    public static PhisEndpointVerificationResult rejected(String failureSummary) {
        return new PhisEndpointVerificationResult(null, null, failureSummary);
    }

    /**
     * 判断100-008来源机构与100-003能力是否均已明确通过。
     *
     * @return 已确认通过时为{@code true}
     */
    public boolean verified() {
        return failureSummary == null;
    }
}
