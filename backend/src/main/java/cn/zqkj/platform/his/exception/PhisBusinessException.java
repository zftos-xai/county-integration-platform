package cn.zqkj.platform.his.exception;

/**
 * 表示基层HIS已明确处理请求但拒绝返回业务数据。
 *
 * <p>该异常与通信异常严格区分：调用已获得可解释结果，因此后续批次可以记录为失败，
 * 而不能按“结果未知”保留活动范围。</p>
 */
public class PhisBusinessException extends RuntimeException {

    /**
     * 创建HIS已明确拒绝业务请求的异常。
     *
     * @param message 不包含服务地址、凭证和完整业务正文的失败说明
     */
    public PhisBusinessException(String message) {
        super(message);
    }
}
