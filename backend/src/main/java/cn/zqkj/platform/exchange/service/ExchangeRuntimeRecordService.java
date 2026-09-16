package cn.zqkj.platform.exchange.service;

import cn.zqkj.platform.exchange.domain.model.ExchangeRuntimeRecord;

/**
 * 定义交换最小运行事实写入服务。
 */
public interface ExchangeRuntimeRecordService {

    /** @param record 已确认最终结果的运行事实 @return 请求编号 */
    String record(ExchangeRuntimeRecord record);
}
