-- 业务域: exchange
-- 主版本: V0100
-- 脚本类型: MAIN
-- 变更说明: 建立交换运行记录业务的首个数据库主版本
-- 需求依据: 项目开发PRD第6章运行记录与交换结果
-- 数据边界: 仅保存最小运行事实和不含敏感内容的摘要，不保存完整XML、JSON、病历或报告正文
-- 时间规则: received_at和processed_at统一保存UTC时间，应用层负责时区转换
-- 回退方案: 首次部署失败时删除本脚本已创建的索引和表；共享环境执行前由DBA复核

-- 表中文名称: 交换记录表
-- 表用途: 保存一次真实目标系统调用的最小运行事实，不承载技术过程状态或通用重试状态
CREATE TABLE exch_exchange_record (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_exch_exchange_record PRIMARY KEY, -- 交换记录主键：平台内部自增主键
    request_id NVARCHAR(64) NOT NULL, -- 请求编号：跨组件定位单次调用的请求编号
    interface_code NVARCHAR(64) NOT NULL, -- 接口代码：正式接口事件码或交易码
    source_system_code NVARCHAR(64) NOT NULL, -- 来源系统代码：实际调用方系统标识
    target_system_code NVARCHAR(64) NOT NULL, -- 目标系统代码：实际目标系统标识
    organization_code NVARCHAR(64) NOT NULL, -- 机构代码：平台统一机构代码及机构数据范围
    source_record_id NVARCHAR(128) NOT NULL, -- 来源业务记录编号：不包含患者正文的业务记录引用
    exchange_result NVARCHAR(16) NOT NULL, -- 交换结果：成功、明确失败、无响应或响应不可解析
    result_code NVARCHAR(64) NULL, -- 结果代码：目标系统返回码；无响应或请求发出前失败时为空
    result_message NVARCHAR(500) NULL, -- 结果说明：不包含完整医疗正文的必要结果说明
    duration_ms BIGINT NOT NULL, -- 处理耗时（毫秒）：从调用开始至确认最终结果的耗时毫秒数
    request_summary NVARCHAR(500) NULL, -- 请求摘要：不包含完整医疗正文的脱敏请求摘要
    communication_error_summary NVARCHAR(500) NULL, -- 通信异常摘要：不包含地址、凭证和正文的通信异常摘要
    received_at DATETIME2(3) NOT NULL, -- 接收时间：平台开始处理本次调用的UTC时间
    processed_at DATETIME2(3) NOT NULL, -- 处理完成时间：平台确认最终交换结果的UTC时间
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_exch_record_created DEFAULT SYSUTCDATETIME(), -- 创建时间：运行记录写入平台库的UTC时间
    row_version ROWVERSION, -- 并发版本：SQL Server并发校验版本，不表示业务版本
    CONSTRAINT uq_exch_record_request UNIQUE (request_id), -- 防止同一请求编号重复形成运行记录
    CONSTRAINT ck_exch_record_result CHECK (exchange_result IN ('SUCCESS', 'FAILURE', 'NO_RESPONSE', 'INVALID_RESPONSE')), -- 区分已收到但不可解析的响应
    CONSTRAINT ck_exch_record_duration CHECK (duration_ms >= 0), -- 禁止保存负数调用耗时
    CONSTRAINT ck_exch_record_time CHECK (processed_at >= received_at) -- 禁止完成时间早于开始时间
);

-- 索引用途: 支持管理端按机构和接收时间倒序查询最近运行记录
CREATE INDEX ix_exch_record_org_received
    ON exch_exchange_record (organization_code, received_at DESC)
    INCLUDE (interface_code, source_record_id, exchange_result, processed_at, duration_ms);

-- 索引用途: 支持按机构、最终结果和时间范围筛选运行记录
CREATE INDEX ix_exch_record_org_result_received
    ON exch_exchange_record (organization_code, exchange_result, received_at DESC)
    INCLUDE (interface_code, source_record_id, request_id, duration_ms);

DECLARE @object_descriptions TABLE (
    object_type NVARCHAR(16) NOT NULL,
    table_name SYSNAME NOT NULL,
    object_name SYSNAME NULL,
    description NVARCHAR(1000) NOT NULL
);

INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
VALUES
    (N'TABLE', N'exch_exchange_record', NULL, N'交换记录表'),
    (N'COLUMN', N'exch_exchange_record', N'id', N'交换记录主键'),
    (N'COLUMN', N'exch_exchange_record', N'request_id', N'请求编号'),
    (N'COLUMN', N'exch_exchange_record', N'interface_code', N'接口代码'),
    (N'COLUMN', N'exch_exchange_record', N'source_system_code', N'来源系统代码'),
    (N'COLUMN', N'exch_exchange_record', N'target_system_code', N'目标系统代码'),
    (N'COLUMN', N'exch_exchange_record', N'organization_code', N'机构代码'),
    (N'COLUMN', N'exch_exchange_record', N'source_record_id', N'来源业务记录编号'),
    (N'COLUMN', N'exch_exchange_record', N'exchange_result', N'交换结果'),
    (N'COLUMN', N'exch_exchange_record', N'result_code', N'结果代码'),
    (N'COLUMN', N'exch_exchange_record', N'result_message', N'结果说明'),
    (N'COLUMN', N'exch_exchange_record', N'duration_ms', N'处理耗时（毫秒）'),
    (N'COLUMN', N'exch_exchange_record', N'request_summary', N'请求摘要'),
    (N'COLUMN', N'exch_exchange_record', N'communication_error_summary', N'通信异常摘要'),
    (N'COLUMN', N'exch_exchange_record', N'received_at', N'接收时间'),
    (N'COLUMN', N'exch_exchange_record', N'processed_at', N'处理完成时间'),
    (N'COLUMN', N'exch_exchange_record', N'created_at', N'创建时间'),
    (N'COLUMN', N'exch_exchange_record', N'row_version', N'并发版本'),
    (N'CONSTRAINT', N'exch_exchange_record', N'pk_exch_exchange_record', N'保证每条交换运行记录具有唯一的平台内部主键。'),
    (N'CONSTRAINT', N'exch_exchange_record', N'uq_exch_record_request', N'保证请求编号唯一，防止同一请求重复形成运行记录。'),
    (N'CONSTRAINT', N'exch_exchange_record', N'ck_exch_record_result', N'区分成功、明确失败、无响应和已收到但无法解析的响应。'),
    (N'CONSTRAINT', N'exch_exchange_record', N'ck_exch_record_duration', N'禁止保存负数调用耗时。'),
    (N'CONSTRAINT', N'exch_exchange_record', N'ck_exch_record_time', N'禁止处理完成时间早于接收时间。'),
    (N'CONSTRAINT', N'exch_exchange_record', N'df_exch_record_created', N'未显式提供时，以SQL Server当前UTC时间作为运行记录写入时间。'),
    (N'INDEX', N'exch_exchange_record', N'ix_exch_record_org_received', N'支持管理端按机构和接收时间倒序查询最近运行记录。'),
    (N'INDEX', N'exch_exchange_record', N'ix_exch_record_org_result_received', N'支持按机构、最终结果和接收时间范围筛选运行记录。');

DECLARE @object_type NVARCHAR(16);
DECLARE @table_name SYSNAME;
DECLARE @object_name SYSNAME;
DECLARE @resolved_object_name SYSNAME;
DECLARE @description NVARCHAR(1000);

DECLARE description_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT object_type, table_name, object_name, description
    FROM @object_descriptions;

OPEN description_cursor;
FETCH NEXT FROM description_cursor INTO @object_type, @table_name, @object_name, @description;

WHILE @@FETCH_STATUS = 0
BEGIN
    SET @resolved_object_name = @object_name;

    IF @object_type = N'TABLE'
    BEGIN
        IF EXISTS (
            SELECT 1
            FROM sys.fn_listextendedproperty(N'MS_Description', N'SCHEMA', N'dbo', N'TABLE', @table_name, NULL, NULL)
        )
            EXEC sys.sp_updateextendedproperty @name=N'MS_Description', @value=@description,
                @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name;
        ELSE
            EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=@description,
                @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name;
    END
    ELSE
    BEGIN
        IF @resolved_object_name IS NULL
            RAISERROR(N'无法解析对象 %s.%s 的名称。', 16, 1, @table_name, @object_name);

        IF EXISTS (
            SELECT 1
            FROM sys.fn_listextendedproperty(N'MS_Description', N'SCHEMA', N'dbo', N'TABLE', @table_name, @object_type, @resolved_object_name)
        )
            EXEC sys.sp_updateextendedproperty @name=N'MS_Description', @value=@description,
                @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name,
                @level2type=@object_type, @level2name=@resolved_object_name;
        ELSE
            EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=@description,
                @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name,
                @level2type=@object_type, @level2name=@resolved_object_name;
    END;

    FETCH NEXT FROM description_cursor INTO @object_type, @table_name, @object_name, @description;
END;

CLOSE description_cursor;
DEALLOCATE description_cursor;
