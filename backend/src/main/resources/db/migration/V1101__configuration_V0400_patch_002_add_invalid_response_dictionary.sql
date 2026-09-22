-- 业务域: configuration
-- 脚本类型: PATCH
-- 归属主版本: V0400
-- 补丁序号: 002
-- 变更说明: 为响应不可解析的交换结果增加字典项
-- 需求依据: 交换记录新增INVALID_RESPONSE结果后管理端需要明确展示
-- 数据边界: 只增加固定枚举项，不保存接口响应正文
-- 时间规则: 字典创建及更新时间由现有UTC默认值生成
-- 回退方案: 确认无引用后删除该字典项

DECLARE @exchange_result_type_id BIGINT;
SELECT @exchange_result_type_id = id FROM dbo.sys_dictionary_type WHERE type_code = N'EXCHANGE_RESULT';
IF @exchange_result_type_id IS NULL
    THROW 51000, N'交换结果字典类型不存在，不能新增响应不可解析字典项', 1;

INSERT INTO dbo.sys_dictionary_item (
    dictionary_type_id, item_code, item_label, sort_order, is_enabled, created_by, updated_by
)
VALUES (@exchange_result_type_id, N'INVALID_RESPONSE', N'响应不可解析', 40, 1, N'SYSTEM', N'SYSTEM');
