package cn.zqkj.platform.modules.exchange.application;

import cn.zqkj.platform.foundation.web.error.InvalidRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 校验并保存外部系统调用的最小运行事实。
 *
 * <p>本服务不执行外部调用、不自动重试，也不保存完整业务报文。调用方应在目标调用结束并能够判断
 * 成功、失败或无响应后使用本服务。</p>
 */
@Service
public class ExchangeRuntimeRecordService {

    private static final int REQUEST_ID_MAX_LENGTH = 64;
    private static final int INTERFACE_CODE_MAX_LENGTH = 64;
    private static final int SYSTEM_CODE_MAX_LENGTH = 64;
    private static final int ORGANIZATION_CODE_MAX_LENGTH = 64;
    private static final int SOURCE_RECORD_ID_MAX_LENGTH = 128;
    private static final int TARGET_RESULT_CODE_MAX_LENGTH = 64;
    private static final int SUMMARY_MAX_LENGTH = 500;

    private final ExchangeRuntimeRecordRepository repository;

    /**
     * 创建运行记录服务。
     *
     * @param repository 交换运行记录写入边界
     */
    public ExchangeRuntimeRecordService(ExchangeRuntimeRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * 保存一次已经结束的真实目标调用结果。
     *
     * <p>请求编号由数据库唯一约束防止重复。写入在独立短事务中完成，不应包围外部系统调用。</p>
     *
     * @param record 待保存的最小运行记录
     * @return 已保存记录的请求编号
     * @throws InvalidRequestException 字段缺失、超长、时间倒置、耗时为负或无响应事实矛盾时抛出
     * @throws IllegalStateException 持久化层没有插入恰好一条记录时抛出
     */
    @Transactional
    public String record(ExchangeRuntimeRecord record) {
        validate(record);
        int insertedRows = repository.save(record);
        if (insertedRows != 1) {
            throw new IllegalStateException("Exchange runtime record was not inserted exactly once");
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
        if (record == null) {
            throw new InvalidRequestException("exchange runtime record is required");
        }
        requireText(record.requestId(), "requestId", REQUEST_ID_MAX_LENGTH);
        requireText(record.interfaceCode(), "interfaceCode", INTERFACE_CODE_MAX_LENGTH);
        requireText(record.callerSystemCode(), "callerSystemCode", SYSTEM_CODE_MAX_LENGTH);
        requireText(record.targetSystemCode(), "targetSystemCode", SYSTEM_CODE_MAX_LENGTH);
        requireText(record.organizationCode(), "organizationCode", ORGANIZATION_CODE_MAX_LENGTH);
        requireText(record.sourceRecordId(), "sourceRecordId", SOURCE_RECORD_ID_MAX_LENGTH);
        if (record.result() == null) {
            throw new InvalidRequestException("result is required");
        }
        requireOptionalLength(record.targetResultCode(), "targetResultCode", TARGET_RESULT_CODE_MAX_LENGTH);
        requireOptionalLength(record.resultMessage(), "resultMessage", SUMMARY_MAX_LENGTH);
        requireOptionalLength(record.requestSummary(), "requestSummary", SUMMARY_MAX_LENGTH);
        requireOptionalLength(
                record.communicationErrorSummary(),
                "communicationErrorSummary",
                SUMMARY_MAX_LENGTH
        );
        if (record.durationMs() < 0) {
            throw new InvalidRequestException("durationMs must not be negative");
        }
        if (record.receivedAt() == null || record.processedAt() == null) {
            throw new InvalidRequestException("receivedAt and processedAt are required");
        }
        if (record.processedAt().isBefore(record.receivedAt())) {
            throw new InvalidRequestException("processedAt must not be before receivedAt");
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
        if (hasText(record.targetResultCode())) {
            throw new InvalidRequestException("NO_RESPONSE must not contain a target result code");
        }
        if (!hasText(record.communicationErrorSummary())) {
            throw new InvalidRequestException("NO_RESPONSE requires a communication error summary");
        }
    }

    /**
     * 校验必填文本及数据库字段长度。
     *
     * @param value 字段值
     * @param fieldName 用于受控错误日志的字段名
     * @param maxLength 数据库允许的最大字符数
     * @throws InvalidRequestException 字段为空或超长时抛出
     */
    private void requireText(String value, String fieldName, int maxLength) {
        if (!hasText(value)) {
            throw new InvalidRequestException(fieldName + " is required");
        }
        requireOptionalLength(value, fieldName, maxLength);
    }

    /**
     * 校验可选文本的数据库字段长度。
     *
     * @param value 字段值；为空时跳过
     * @param fieldName 用于受控错误日志的字段名
     * @param maxLength 数据库允许的最大字符数
     * @throws InvalidRequestException 字段超长时抛出
     */
    private void requireOptionalLength(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new InvalidRequestException(fieldName + " exceeds maximum length");
        }
    }

    /**
     * 判断文本是否包含至少一个非空白字符。
     *
     * @param value 待判断文本
     * @return 文本非空且不全为空白时返回 {@code true}
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
