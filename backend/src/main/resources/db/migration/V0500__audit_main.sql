-- 业务域: audit
-- 主版本: V0500
-- 脚本类型: MAIN
-- 变更说明: 建立不可由普通管理功能修改或删除的管理审计记录初始结构
-- 需求依据: 开发批次任务计划DEV-009、平台基础管理功能技术设计
-- 数据边界: 保存操作人和机构记录、目标、结果及不含敏感内容的摘要，不保存密码、Token、验证码、凭证引用、私钥或业务正文
-- 时间规则: occurred_at保存UTC时间，精度为毫秒
-- 回退方案: 首次部署失败时删除audit_management_event；共享环境执行后只允许新增归属V0500的版本化补丁

-- 表中文名称: 管理审计事件表
-- 表用途: 追加保存平台管理变更与安全失败事件；普通业务服务不提供更新或删除接口
CREATE TABLE audit_management_event (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_audit_management_event PRIMARY KEY, -- 审计主键：只增不改事件标识
    occurred_at DATETIME2(3) NOT NULL CONSTRAINT df_audit_management_event_occurred DEFAULT SYSUTCDATETIME(), -- 发生时间：数据库生成UTC毫秒时间
    actor_user_id BIGINT NULL, -- 操作人主键：认证成功时保存；登录失败等无法确认登录用户时为空
    actor_login_snapshot NVARCHAR(128) NOT NULL, -- 操作时记录的用户信息：登录用户登录名或脱敏登录提示
    organization_id BIGINT NULL, -- 机构主键：事件归属机构；全局事件或未知时为空
    organization_code_snapshot NVARCHAR(64) NULL, -- 机构代码快照：机构后续变更时仍保留当时范围
    action_code NVARCHAR(64) NOT NULL, -- 动作代码：服务端定义的稳定审计动作
    target_type NVARCHAR(64) NOT NULL, -- 目标类型：PARAMETER、DICTIONARY_TYPE等稳定类型
    target_id NVARCHAR(128) NOT NULL, -- 目标标识：脱敏且可定位的业务标识
    result_code NVARCHAR(16) NOT NULL, -- 结果代码：SUCCESS或FAILURE
    change_summary NVARCHAR(1000) NOT NULL, -- 变更摘要：只保存非敏感字段和影响，不保存秘密或业务正文
    request_id NVARCHAR(64) NULL, -- 请求编号：关联应用日志；无法取得时为空
    CONSTRAINT ck_audit_management_event_actor_not_blank CHECK (LEN(LTRIM(RTRIM(actor_login_snapshot))) > 0), -- 禁止空白操作时记录的用户信息
    CONSTRAINT ck_audit_management_event_action_not_blank CHECK (LEN(LTRIM(RTRIM(action_code))) > 0), -- 禁止空白动作代码
    CONSTRAINT ck_audit_management_event_target_type_not_blank CHECK (LEN(LTRIM(RTRIM(target_type))) > 0), -- 禁止空白目标类型
    CONSTRAINT ck_audit_management_event_target_id_not_blank CHECK (LEN(LTRIM(RTRIM(target_id))) > 0), -- 禁止空白目标标识
    CONSTRAINT ck_audit_management_event_result CHECK (result_code IN (N'SUCCESS', N'FAILURE')), -- 限制审计结果代码
    CONSTRAINT ck_audit_management_event_summary_not_blank CHECK (LEN(LTRIM(RTRIM(change_summary))) > 0) -- 禁止空白变更摘要
);

-- 索引用途: 支持按机构范围和时间倒序查询管理事件
CREATE INDEX ix_audit_management_event_organization_time
    ON audit_management_event (organization_id, occurred_at DESC, id DESC)
    INCLUDE (action_code, target_type, target_id, result_code, actor_login_snapshot);

-- 索引用途: 支持按目标对象追溯全部管理变更
CREATE INDEX ix_audit_management_event_target_time
    ON audit_management_event (target_type, target_id, occurred_at DESC, id DESC)
    INCLUDE (action_code, result_code, actor_login_snapshot, organization_id);

DECLARE @object_descriptions TABLE (object_type NVARCHAR(16) NOT NULL, table_name SYSNAME NOT NULL,
    object_name SYSNAME NULL, description NVARCHAR(1000) NOT NULL);
INSERT INTO @object_descriptions (object_type, table_name, object_name, description) VALUES
    (N'TABLE', N'audit_management_event', NULL, N'管理审计事件表'),
    (N'COLUMN', N'audit_management_event', N'id', N'审计主键'),
    (N'COLUMN', N'audit_management_event', N'occurred_at', N'发生时间'),
    (N'COLUMN', N'audit_management_event', N'actor_user_id', N'操作人主键'),
    (N'COLUMN', N'audit_management_event', N'actor_login_snapshot', N'操作时记录的用户信息'),
    (N'COLUMN', N'audit_management_event', N'organization_id', N'机构主键'),
    (N'COLUMN', N'audit_management_event', N'organization_code_snapshot', N'机构代码快照'),
    (N'COLUMN', N'audit_management_event', N'action_code', N'动作代码'),
    (N'COLUMN', N'audit_management_event', N'target_type', N'目标类型'),
    (N'COLUMN', N'audit_management_event', N'target_id', N'目标标识'),
    (N'COLUMN', N'audit_management_event', N'result_code', N'结果代码'),
    (N'COLUMN', N'audit_management_event', N'change_summary', N'脱敏变更摘要'),
    (N'COLUMN', N'audit_management_event', N'request_id', N'请求编号'),
    (N'CONSTRAINT', N'audit_management_event', N'pk_audit_management_event', N'保证每个审计事件具有唯一内部主键。'),
    (N'CONSTRAINT', N'audit_management_event', N'df_audit_management_event_occurred', N'默认使用数据库当前UTC时间作为发生时间。'),
    (N'CONSTRAINT', N'audit_management_event', N'ck_audit_management_event_actor_not_blank', N'禁止保存空白操作时记录的用户信息。'),
    (N'CONSTRAINT', N'audit_management_event', N'ck_audit_management_event_action_not_blank', N'禁止保存空白动作代码。'),
    (N'CONSTRAINT', N'audit_management_event', N'ck_audit_management_event_target_type_not_blank', N'禁止保存空白目标类型。'),
    (N'CONSTRAINT', N'audit_management_event', N'ck_audit_management_event_target_id_not_blank', N'禁止保存空白目标标识。'),
    (N'CONSTRAINT', N'audit_management_event', N'ck_audit_management_event_result', N'限制结果为成功或失败。'),
    (N'CONSTRAINT', N'audit_management_event', N'ck_audit_management_event_summary_not_blank', N'禁止保存空白不含敏感内容的摘要。'),
    (N'INDEX', N'audit_management_event', N'ix_audit_management_event_organization_time', N'支持按机构范围和时间查询审计事件。'),
    (N'INDEX', N'audit_management_event', N'ix_audit_management_event_target_time', N'支持按目标对象追溯管理变更。');

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
