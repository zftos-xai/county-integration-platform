-- 业务域: exchange
-- 主版本: V0100
-- 脚本类型: MAIN
-- 变更说明: 建立交换运行记录业务的首个数据库主版本
-- 需求依据: 项目开发PRD第6章运行记录与交换结果
-- 数据边界: 仅保存最小运行事实和脱敏摘要，不保存完整XML、JSON、病历或报告正文
-- 时间口径: received_at和processed_at统一保存UTC时间，应用层负责时区转换
-- 回退方案: 首次部署失败时删除本脚本已创建的索引和表；共享环境执行前由DBA复核

-- 表用途: 保存一次真实目标系统调用的最小运行事实，不承载技术过程状态或通用重试状态
CREATE TABLE exch_exchange_record (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, -- 平台内部自增主键
    request_id NVARCHAR(64) NOT NULL, -- 跨组件定位单次调用的请求编号
    interface_code NVARCHAR(64) NOT NULL, -- 正式接口事件码或交易码
    source_system_code NVARCHAR(64) NOT NULL, -- 实际调用方系统标识
    target_system_code NVARCHAR(64) NOT NULL, -- 实际目标系统标识
    organization_code NVARCHAR(64) NOT NULL, -- 平台统一机构代码及机构数据范围
    source_record_id NVARCHAR(128) NOT NULL, -- 不包含患者正文的业务记录引用
    exchange_result NVARCHAR(16) NOT NULL, -- 最终结果：SUCCESS、FAILURE或NO_RESPONSE
    result_code NVARCHAR(64) NULL, -- 目标系统返回码；无响应或请求发出前失败时为空
    result_message NVARCHAR(500) NULL, -- 不包含完整医疗正文的必要结果说明
    duration_ms BIGINT NOT NULL, -- 从调用开始至确认最终结果的耗时毫秒数
    request_summary NVARCHAR(500) NULL, -- 不包含完整医疗正文的脱敏请求摘要
    communication_error_summary NVARCHAR(500) NULL, -- 不包含地址、凭证和正文的通信异常摘要
    received_at DATETIME2(3) NOT NULL, -- 平台开始处理本次调用的UTC时间
    processed_at DATETIME2(3) NOT NULL, -- 平台确认最终交换结果的UTC时间
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_exch_record_created DEFAULT SYSUTCDATETIME(), -- 运行记录写入平台库的UTC时间
    row_version ROWVERSION, -- SQL Server并发校验版本，不表示业务版本
    CONSTRAINT uq_exch_record_request UNIQUE (request_id), -- 防止同一请求编号重复形成运行记录
    CONSTRAINT ck_exch_record_result CHECK (exchange_result IN ('SUCCESS', 'FAILURE', 'NO_RESPONSE')), -- 限定PRD确认的三类最终结果
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
