package cn.zqkj.platform.exchange.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.exchange.domain.model.ExchangeResult;
import cn.zqkj.platform.exchange.domain.model.ExchangeRuntimeRecord;
import cn.zqkj.platform.exchange.mapper.ExchangeRecordMapper;
import cn.zqkj.platform.exchange.service.ExchangeRuntimeRecordService;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 校验并保存外部系统调用的最小运行事实。
 *
 * <p>本服务不执行外部调用、不自动重试，也不保存完整业务报文。调用方应在目标调用结束并能够判断
 * 成功、失败或无响应后使用本服务。</p>
 */
@Service
public class ExchangeRuntimeRecordServiceImpl implements ExchangeRuntimeRecordService {


    private final ExchangeRecordMapper mapper;
    private final Validator validator;

    /**
     * 创建运行记录服务。
     *
     * @param mapper 交换运行记录写入边界
     * @param validator 内部调用事实的结构约束校验器，不依赖 HTTP 入口
     */
    public ExchangeRuntimeRecordServiceImpl(ExchangeRecordMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    /**
     * 保存一次已经结束的真实目标调用结果。
     *
     * <p>请求编号由数据库唯一约束防止重复。调用方应在外部调用结束后进入此写入事务；本方法不发起网络请求。</p>
     *
     * @param record 待保存的最小运行记录
     * @return 已保存记录的请求编号
     * @throws InvalidRequestException 字段缺失、超长、时间倒置、耗时为负或无响应事实矛盾时抛出
     * @throws IllegalStateException 持久化层没有插入恰好一条记录时抛出
     */
    @Transactional
    @Override
    public String record(ExchangeRuntimeRecord record) {
        validate(record);
        int insertedRows = mapper.insertRuntimeRecord(record);
        if (insertedRows != 1) {
            throw new IllegalStateException("交换运行记录写入次数不是 1 次");
        }
        return record.requestId();
    }

    /**
     * 校验运行记录的结构边界和三类结果事实一致性。
     *
     * @param record 待校验的运行记录
     * @throws InvalidRequestException 记录不满足最小运行事实规则时抛出
     */
    private void validate(ExchangeRuntimeRecord record) {
        if (record == null || !validator.validate(record).isEmpty()) {
            throw new InvalidRequestException("交换运行记录缺少必填信息或超过允许范围");
        }
        if (record.processedAt().isBefore(record.receivedAt())) {
            throw new InvalidRequestException("processedAt 不能早于 receivedAt");
        }
        validateNoResponse(record);
    }

    /**
     * 校验无响应记录没有伪造目标返回码，并保留可定位的通信异常摘要。
     *
     * @param record 已完成通用字段校验的运行记录
     * @throws InvalidRequestException 无响应记录与目标响应事实矛盾时抛出
     */
    private void validateNoResponse(ExchangeRuntimeRecord record) {
        if (record.result() != ExchangeResult.NO_RESPONSE) {
            return;
        }
        if (Func.isNotBlank(record.targetResultCode())) {
            throw new InvalidRequestException("结果为 NO_RESPONSE 时不能包含目标系统结果代码");
        }
        if (Func.isBlank(record.communicationErrorSummary())) {
            throw new InvalidRequestException("结果为 NO_RESPONSE 时必须提供通信错误摘要");
        }
    }

}
