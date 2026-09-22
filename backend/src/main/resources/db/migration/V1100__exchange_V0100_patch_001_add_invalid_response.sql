-- 业务域: exchange
-- 脚本类型: PATCH
-- 归属主版本: V0100
-- 补丁序号: 001
-- 变更说明: 区分无响应与已收到但无法解析的响应
-- 需求依据: 100-004/100-005调用排障必须准确记录响应是否抵达
-- 数据边界: 仅增加交换结果枚举，不保存原始医疗报文
-- 时间规则: 不改变既有UTC时间字段
-- 回退方案: 确认没有INVALID_RESPONSE记录后恢复原检查约束与说明

ALTER TABLE dbo.exch_exchange_record DROP CONSTRAINT ck_exch_record_result;
ALTER TABLE dbo.exch_exchange_record WITH CHECK ADD CONSTRAINT ck_exch_record_result
    CHECK (exchange_result IN ('SUCCESS', 'FAILURE', 'NO_RESPONSE', 'INVALID_RESPONSE')); -- 已收到但不可解析的响应不能误记为无响应

DECLARE @object_descriptions TABLE (object_type NVARCHAR(16), table_name SYSNAME, object_name SYSNAME, description NVARCHAR(1000));
INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
VALUES (N'CONSTRAINT', N'exch_exchange_record', N'ck_exch_record_result', N'区分成功、明确失败、无响应和已收到但无法解析的响应。');

EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'区分成功、明确失败、无响应和已收到但无法解析的响应。',
    @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=N'exch_exchange_record',
    @level2type=N'CONSTRAINT', @level2name=N'ck_exch_record_result';
