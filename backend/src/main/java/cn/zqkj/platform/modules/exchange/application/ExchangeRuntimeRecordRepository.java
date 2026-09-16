package cn.zqkj.platform.modules.exchange.application;

/**
 * 交换运行记录写入的持久化边界。
 *
 * <p>实现只能保存最小运行事实，不得在该边界扩展完整业务正文或通用重试状态。</p>
 */
public interface ExchangeRuntimeRecordRepository {

    /**
     * 保存一次已经确定最终结果的外部调用记录。
     *
     * @param record 已校验且不包含完整医疗正文的运行记录
     * @return 实际插入的记录数量，应为 1
     */
    int save(ExchangeRuntimeRecord record);
}
