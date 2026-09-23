-- 业务域: icd10
-- 主版本: V1200
-- 脚本类型: PATCH
-- 归属主版本: V1200
-- 补丁序号: 003
-- 变更说明: 为公共ICD10同步批次保存每次100-006和100-007调用的脱敏请求及受控响应摘要。
-- 需求依据: 批次详情需要分别展示同步结果、HIS请求追踪和管理审计；现有批次结果仅保存类别汇总。
-- 数据边界: 仅保存交易码、分页范围、脱敏参数、受控响应摘要和耗时；不保存SOAP完整XML、地址、账号、密码、授权码、Token或患者正文。
-- 时间规则: requested_at和completed_at均保存UTC DATETIME2(3)；每行表示一次真实发送后的终态调用事实。
-- 回退方案: 停止读取和写入本表，保留已写入的追踪事实；不删除批次、目录和审计数据，待完成备份与影响评估后按医院流程回退。

-- 表中文名称: 国际疾病分类第十版同步调用记录表
-- 表用途: 记录一个公共ICD10同步批次中每次100-006/100-007真实HIS调用的脱敏输入及最终通信事实；不承担原始报文归档、凭证保存或管理审计职责。
CREATE TABLE md_icd10_his_invocation (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_md_icd10_his_invocation PRIMARY KEY, -- 调用事实内部主键
    batch_id BIGINT NOT NULL, -- 所属平台公共ICD10同步批次主键
    diagnosis_category NVARCHAR(16) NOT NULL, -- 本次调用的来源疾病类别：0西医、1中医
    invocation_sequence INT NOT NULL, -- 同一批次同一类别内从1开始的稳定调用顺序
    trade_code NVARCHAR(16) NOT NULL, -- HIS交易代码，仅允许100-006或100-007
    page_start BIGINT NULL, -- 100-006请求起始行，100-007数量查询为空
    page_end BIGINT NULL, -- 100-006请求结束行，100-007数量查询为空
    request_summary NVARCHAR(500) NOT NULL, -- 已脱敏请求参数摘要，不保存SOAP完整报文或凭证
    outcome_status NVARCHAR(32) NOT NULL, -- 调用终态：SUCCESS、FAILURE、NO_RESPONSE或INVALID_RESPONSE
    result_code NVARCHAR(64) NULL, -- HIS明确响应时返回的受控结果代码，无响应或协议错误为空
    response_summary NVARCHAR(500) NULL, -- 已脱敏响应或错误摘要，不保存HIS原始报文
    returned_count BIGINT NULL, -- 100-006本页返回数量或100-007声明数量，无法确认时为空
    duration_ms BIGINT NOT NULL, -- 从发起到平台确认终态的耗时，单位毫秒
    requested_at DATETIME2(3) NOT NULL, -- 平台开始本次HIS调用的UTC时间
    completed_at DATETIME2(3) NOT NULL, -- 平台确认本次调用终态的UTC时间
    CONSTRAINT fk_md_icd10_his_invocation_batch FOREIGN KEY (batch_id) REFERENCES md_sync_batch(id), -- 保证调用事实归属存在的同步批次
    CONSTRAINT uq_md_icd10_his_invocation_sequence UNIQUE (batch_id, diagnosis_category, invocation_sequence), -- 防止同一批次类别的调用顺序重复写入
    CONSTRAINT ck_md_icd10_his_invocation_category CHECK (diagnosis_category IN (N'0', N'1')), -- 限定接口确认的西医和中医类别
    CONSTRAINT ck_md_icd10_his_invocation_trade CHECK (trade_code IN (N'100-006', N'100-007')), -- 限定ICD10已确认交易码
    CONSTRAINT ck_md_icd10_his_invocation_page CHECK ((trade_code = N'100-007' AND page_start IS NULL AND page_end IS NULL) OR (trade_code = N'100-006' AND page_start IS NOT NULL AND page_end IS NOT NULL AND page_start >= 1 AND page_end > page_start)), -- 约束数量查询和分页查询的范围形态
    CONSTRAINT ck_md_icd10_his_invocation_outcome CHECK (outcome_status IN (N'SUCCESS', N'FAILURE', N'NO_RESPONSE', N'INVALID_RESPONSE')), -- 限定可确认的HIS调用终态
    CONSTRAINT ck_md_icd10_his_invocation_counts CHECK ((returned_count IS NULL OR returned_count >= 0) AND duration_ms >= 0), -- 禁止负数数量和耗时
    CONSTRAINT ck_md_icd10_his_invocation_text CHECK (LEN(LTRIM(RTRIM(request_summary))) > 0) -- 禁止空白请求摘要
);

-- 索引用途: 支持批次详情按诊断类别、调用顺序稳定分页读取HIS调用事实。
CREATE INDEX ix_md_icd10_his_invocation_batch_read
    ON md_icd10_his_invocation (batch_id, diagnosis_category, invocation_sequence, id)
    INCLUDE (trade_code, page_start, page_end, outcome_status, returned_count, duration_ms, requested_at);

DECLARE @object_descriptions TABLE (object_type NVARCHAR(16) NOT NULL, table_name SYSNAME NOT NULL, object_name SYSNAME NULL, description NVARCHAR(1000) NOT NULL);
INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
VALUES
    (N'TABLE', N'md_icd10_his_invocation', NULL, N'国际疾病分类第十版同步调用记录表'),
    (N'COLUMN', N'md_icd10_his_invocation', N'id', N'HIS调用记录主键'), (N'COLUMN', N'md_icd10_his_invocation', N'batch_id', N'同步批次主键'), (N'COLUMN', N'md_icd10_his_invocation', N'diagnosis_category', N'诊断类别'), (N'COLUMN', N'md_icd10_his_invocation', N'invocation_sequence', N'类别内调用顺序'), (N'COLUMN', N'md_icd10_his_invocation', N'trade_code', N'HIS交易代码'), (N'COLUMN', N'md_icd10_his_invocation', N'page_start', N'请求起始行'), (N'COLUMN', N'md_icd10_his_invocation', N'page_end', N'请求结束行'), (N'COLUMN', N'md_icd10_his_invocation', N'request_summary', N'脱敏请求摘要（不保存原始报文和凭证）'), (N'COLUMN', N'md_icd10_his_invocation', N'outcome_status', N'调用终态'), (N'COLUMN', N'md_icd10_his_invocation', N'result_code', N'HIS结果代码'), (N'COLUMN', N'md_icd10_his_invocation', N'response_summary', N'脱敏响应摘要（不保存原始报文）'), (N'COLUMN', N'md_icd10_his_invocation', N'returned_count', N'返回或声明数量'), (N'COLUMN', N'md_icd10_his_invocation', N'duration_ms', N'调用耗时（毫秒）'), (N'COLUMN', N'md_icd10_his_invocation', N'requested_at', N'请求时间（UTC）'), (N'COLUMN', N'md_icd10_his_invocation', N'completed_at', N'完成时间（UTC）'),
    (N'CONSTRAINT', N'md_icd10_his_invocation', N'pk_md_icd10_his_invocation', N'保证HIS调用记录主键唯一。'), (N'CONSTRAINT', N'md_icd10_his_invocation', N'fk_md_icd10_his_invocation_batch', N'保证HIS调用记录归属批次存在。'), (N'CONSTRAINT', N'md_icd10_his_invocation', N'uq_md_icd10_his_invocation_sequence', N'防止同批次同类别调用顺序重复。'), (N'CONSTRAINT', N'md_icd10_his_invocation', N'ck_md_icd10_his_invocation_category', N'限定诊断类别范围。'), (N'CONSTRAINT', N'md_icd10_his_invocation', N'ck_md_icd10_his_invocation_trade', N'限定ICD10交易代码。'), (N'CONSTRAINT', N'md_icd10_his_invocation', N'ck_md_icd10_his_invocation_page', N'保证交易与分页范围匹配。'), (N'CONSTRAINT', N'md_icd10_his_invocation', N'ck_md_icd10_his_invocation_outcome', N'限定HIS调用终态。'), (N'CONSTRAINT', N'md_icd10_his_invocation', N'ck_md_icd10_his_invocation_counts', N'禁止负数数量和耗时。'), (N'CONSTRAINT', N'md_icd10_his_invocation', N'ck_md_icd10_his_invocation_text', N'禁止空白请求摘要。'), (N'INDEX', N'md_icd10_his_invocation', N'ix_md_icd10_his_invocation_batch_read', N'支持批次详情稳定分页读取HIS调用记录。');

DECLARE @object_type NVARCHAR(16), @table_name SYSNAME, @object_name SYSNAME, @description NVARCHAR(1000);
DECLARE description_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT object_type, table_name, object_name, description FROM @object_descriptions;
OPEN description_cursor;
FETCH NEXT FROM description_cursor INTO @object_type, @table_name, @object_name, @description;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF @object_type = N'TABLE'
        EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=@description,
            @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name;
    ELSE
        EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=@description,
            @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name,
            @level2type=@object_type, @level2name=@object_name;
    FETCH NEXT FROM description_cursor INTO @object_type, @table_name, @object_name, @description;
END;
CLOSE description_cursor;
DEALLOCATE description_cursor;
