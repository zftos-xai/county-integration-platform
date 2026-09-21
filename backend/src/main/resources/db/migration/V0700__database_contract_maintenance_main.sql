-- 业务域: database_contract_maintenance
-- 主版本: V0700
-- 脚本类型: MAIN
-- 变更说明: 建立数据库契约正常维护方案、审批、受控DDL执行和复验记录
-- 需求依据: 数据库契约两线治理方案；正常维护线需要分析、方案、审批、执行、事务回滚和复验闭环
-- 数据边界: 仅保存结构差异、服务端生成DDL和操作元数据；不保存数据库凭证、连接串、业务正文或患者数据
-- 时间规则: 创建、审批、执行、复验和更新时间统一保存UTC时间，应用层负责本地化显示
-- 回退方案: 主版本进入共享环境前可先删除明细表再删除方案表；进入共享环境后只能新增归属V0700的连续补丁

-- 表中文名称: 数据库契约维护方案表
-- 表用途: 保存一次正常维护从差异快照、双人审批到事务执行和复验的完整事实；不接受客户端SQL
CREATE TABLE db_contract_plan (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_db_contract_plan PRIMARY KEY, -- 方案主键：平台内部自增主键
    plan_no NVARCHAR(40) NOT NULL, -- 方案编号：用于页面、审计和问题单关联的稳定编号
    status NVARCHAR(24) NOT NULL, -- 方案状态：草稿、已批准、执行中、已完成、失败或已取消
    issue_count INT NOT NULL, -- 差异数量：创建方案时选中的契约差异总数
    executable_count INT NOT NULL, -- 可执行数量：允许由平台生成受控DDL的差异数
    summary NVARCHAR(500) NOT NULL, -- 方案摘要：不包含凭证或业务敏感数据
    created_by NVARCHAR(64) NOT NULL, -- 创建人：生成方案的登录名快照
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_db_contract_plan_created DEFAULT SYSUTCDATETIME(), -- 创建时间：方案生成UTC时间
    approved_by NVARCHAR(64) NULL, -- 审批人：与创建人不同的批准人登录名快照
    approved_at DATETIME2(3) NULL, -- 审批时间：方案批准UTC时间
    approval_note NVARCHAR(500) NULL, -- 审批说明：影响范围、备份点和执行窗口摘要
    executed_by NVARCHAR(64) NULL, -- 执行人：发起受控DDL执行的登录名快照
    executed_at DATETIME2(3) NULL, -- 执行时间：DDL事务完成或失败UTC时间
    verified_at DATETIME2(3) NULL, -- 复验时间：执行后重新扫描确认UTC时间
    failure_message NVARCHAR(1000) NULL, -- 失败摘要：已裁剪且不包含连接凭证或SQL参数
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_db_contract_plan_updated DEFAULT SYSUTCDATETIME(), -- 更新时间：方案状态最后变化UTC时间
    row_version ROWVERSION, -- 并发版本：阻止旧页面重复审批、执行或取消
    CONSTRAINT uq_db_contract_plan_no UNIQUE (plan_no), -- 保证维护方案编号唯一
    CONSTRAINT ck_db_contract_plan_status CHECK (status IN ('DRAFT', 'APPROVED', 'EXECUTING', 'COMPLETED', 'FAILED', 'CANCELLED')), -- 限定维护方案状态机
    CONSTRAINT ck_db_contract_plan_counts CHECK (issue_count > 0 AND executable_count >= 0 AND executable_count <= issue_count), -- 保证差异统计有效
    CONSTRAINT ck_db_contract_plan_approval CHECK ((status = 'DRAFT' AND approved_by IS NULL) OR status <> 'DRAFT') -- 草稿不得伪造审批人
);

-- 索引用途: 支持维护页面按状态和最近更新时间倒序查询方案
CREATE INDEX ix_db_contract_plan_status
    ON db_contract_plan (status, updated_at DESC, id DESC)
    INCLUDE (plan_no, issue_count, executable_count, created_by, approved_by);

-- 表中文名称: 数据库契约维护方案明细表
-- 表用途: 保存方案所选差异、服务端生成DDL预览、执行状态和逐项复验结果
CREATE TABLE db_contract_plan_item (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_db_contract_plan_item PRIMARY KEY, -- 明细主键：平台内部自增主键
    plan_id BIGINT NOT NULL, -- 方案主键：归属数据库契约维护方案
    violation_code NVARCHAR(32) NOT NULL, -- 差异代码：统一检查核心生成的稳定错误码
    object_name NVARCHAR(300) NOT NULL, -- 对象名称：架构、表、字段或Mapper模型路径
    expected_value NVARCHAR(1000) NOT NULL, -- 期望值快照：创建方案时Flyway或映射契约
    actual_value NVARCHAR(1000) NOT NULL, -- 实际值快照：创建方案时数据库或代码检测结果
    direction NVARCHAR(24) NOT NULL, -- 修改方向：数据库、Mapper模型或人工判断
    action_text NVARCHAR(1000) NOT NULL, -- 处理建议：面向开发、DBA或审批人的明确动作
    executable BIT NOT NULL, -- 可执行标志：是否允许平台执行服务端生成DDL
    ddl_preview NVARCHAR(2000) NULL, -- DDL预览：仅服务端可信元数据生成，不接受客户端SQL
    execution_note NVARCHAR(500) NOT NULL, -- 执行说明：允许执行的边界或拒绝自动执行原因
    execution_status NVARCHAR(24) NOT NULL CONSTRAINT df_db_contract_item_status DEFAULT 'PENDING', -- 执行状态：待处理、已执行、需人工处理或已回滚
    verification_status NVARCHAR(24) NOT NULL CONSTRAINT df_db_contract_item_verify DEFAULT 'PENDING', -- 复验状态：待复验、通过或仍存在
    CONSTRAINT fk_db_contract_item_plan FOREIGN KEY (plan_id) REFERENCES db_contract_plan(id), -- 保证差异明细归属已存在方案
    CONSTRAINT uq_db_contract_item_object UNIQUE (plan_id, violation_code, object_name), -- 防止同一方案重复加入相同差异
    CONSTRAINT ck_db_contract_item_direction CHECK (direction IN ('DATABASE', 'MAPPER_OR_MODEL', 'MANUAL_REVIEW')), -- 限定建议修改方向
    CONSTRAINT ck_db_contract_item_execution CHECK (execution_status IN ('PENDING', 'EXECUTED', 'MANUAL_REQUIRED', 'ROLLED_BACK')), -- 限定执行事实
    CONSTRAINT ck_db_contract_item_verification CHECK (verification_status IN ('PENDING', 'PASSED', 'REMAINS')) -- 限定复验事实
);

-- 索引用途: 支持按方案稳定读取全部差异和执行复验状态
CREATE INDEX ix_db_contract_plan_item_plan
    ON db_contract_plan_item (plan_id, id)
    INCLUDE (violation_code, object_name, executable, execution_status, verification_status);

IF NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = N'database-contract:read')
    INSERT INTO sys_permission (permission_code, permission_name) VALUES (N'database-contract:read', N'查询数据库契约维护');
IF NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = N'database-contract:plan')
    INSERT INTO sys_permission (permission_code, permission_name) VALUES (N'database-contract:plan', N'生成和取消数据库契约方案');
IF NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = N'database-contract:approve')
    INSERT INTO sys_permission (permission_code, permission_name) VALUES (N'database-contract:approve', N'审批数据库契约方案');
IF NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = N'database-contract:execute')
    INSERT INTO sys_permission (permission_code, permission_name) VALUES (N'database-contract:execute', N'执行数据库契约受控DDL');

INSERT INTO sys_role_permission (role_id, permission_code, granted_by)
SELECT role_row.id, permission_row.permission_code, N'migration-V0700'
FROM sys_role role_row
CROSS JOIN sys_permission permission_row
WHERE role_row.role_code = N'PLATFORM_ADMIN'
  AND permission_row.permission_code IN (N'database-contract:read', N'database-contract:plan', N'database-contract:approve', N'database-contract:execute')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing_permission
      WHERE existing_permission.role_id = role_row.id
        AND existing_permission.permission_code = permission_row.permission_code
  );

DECLARE @object_descriptions TABLE (
    object_type NVARCHAR(16) NOT NULL,
    table_name SYSNAME NOT NULL,
    object_name SYSNAME NULL,
    description NVARCHAR(1000) NOT NULL
);

INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
VALUES
    (N'TABLE', N'db_contract_plan', NULL, N'数据库契约维护方案表'),
    (N'COLUMN', N'db_contract_plan', N'id', N'方案主键'),
    (N'COLUMN', N'db_contract_plan', N'plan_no', N'可追溯方案编号'),
    (N'COLUMN', N'db_contract_plan', N'status', N'维护方案状态'),
    (N'COLUMN', N'db_contract_plan', N'issue_count', N'选中差异总数'),
    (N'COLUMN', N'db_contract_plan', N'executable_count', N'可在线执行差异数'),
    (N'COLUMN', N'db_contract_plan', N'summary', N'方案业务摘要'),
    (N'COLUMN', N'db_contract_plan', N'created_by', N'创建人登录名快照'),
    (N'COLUMN', N'db_contract_plan', N'created_at', N'创建时间（世界协调时）'),
    (N'COLUMN', N'db_contract_plan', N'approved_by', N'审批人登录名快照'),
    (N'COLUMN', N'db_contract_plan', N'approved_at', N'审批时间（世界协调时）'),
    (N'COLUMN', N'db_contract_plan', N'approval_note', N'审批依据和执行窗口摘要'),
    (N'COLUMN', N'db_contract_plan', N'executed_by', N'执行人登录名快照'),
    (N'COLUMN', N'db_contract_plan', N'executed_at', N'执行时间（世界协调时）'),
    (N'COLUMN', N'db_contract_plan', N'verified_at', N'复验时间（世界协调时）'),
    (N'COLUMN', N'db_contract_plan', N'failure_message', N'不含敏感信息的失败摘要'),
    (N'COLUMN', N'db_contract_plan', N'updated_at', N'更新时间（世界协调时）'),
    (N'COLUMN', N'db_contract_plan', N'row_version', N'并发版本'),
    (N'CONSTRAINT', N'db_contract_plan', N'pk_db_contract_plan', N'保证每个维护方案具有唯一平台主键。'),
    (N'CONSTRAINT', N'db_contract_plan', N'uq_db_contract_plan_no', N'保证维护方案编号唯一。'),
    (N'CONSTRAINT', N'db_contract_plan', N'ck_db_contract_plan_status', N'限定维护方案状态机。'),
    (N'CONSTRAINT', N'db_contract_plan', N'ck_db_contract_plan_counts', N'保证差异总数和可执行数量有效。'),
    (N'CONSTRAINT', N'db_contract_plan', N'ck_db_contract_plan_approval', N'阻止草稿伪造审批人。'),
    (N'CONSTRAINT', N'db_contract_plan', N'df_db_contract_plan_created', N'默认使用SQL Server当前UTC时间作为创建时间。'),
    (N'CONSTRAINT', N'db_contract_plan', N'df_db_contract_plan_updated', N'默认使用SQL Server当前UTC时间作为更新时间。'),
    (N'INDEX', N'db_contract_plan', N'ix_db_contract_plan_status', N'支持按状态和更新时间查询最近维护方案。'),

    (N'TABLE', N'db_contract_plan_item', NULL, N'数据库契约维护方案明细表'),
    (N'COLUMN', N'db_contract_plan_item', N'id', N'明细主键'),
    (N'COLUMN', N'db_contract_plan_item', N'plan_id', N'归属方案主键'),
    (N'COLUMN', N'db_contract_plan_item', N'violation_code', N'稳定差异代码'),
    (N'COLUMN', N'db_contract_plan_item', N'object_name', N'数据库或映射对象名称'),
    (N'COLUMN', N'db_contract_plan_item', N'expected_value', N'期望契约快照'),
    (N'COLUMN', N'db_contract_plan_item', N'actual_value', N'实际检测快照'),
    (N'COLUMN', N'db_contract_plan_item', N'direction', N'建议修改方向'),
    (N'COLUMN', N'db_contract_plan_item', N'action_text', N'处理建议'),
    (N'COLUMN', N'db_contract_plan_item', N'executable', N'是否允许平台在线执行'),
    (N'COLUMN', N'db_contract_plan_item', N'ddl_preview', N'变更语句预览'),
    (N'COLUMN', N'db_contract_plan_item', N'execution_note', N'执行边界或拒绝自动执行原因'),
    (N'COLUMN', N'db_contract_plan_item', N'execution_status', N'执行状态'),
    (N'COLUMN', N'db_contract_plan_item', N'verification_status', N'执行后复验状态'),
    (N'CONSTRAINT', N'db_contract_plan_item', N'pk_db_contract_plan_item', N'保证每条方案明细具有唯一平台主键。'),
    (N'CONSTRAINT', N'db_contract_plan_item', N'fk_db_contract_item_plan', N'保证差异明细归属于已存在的维护方案。'),
    (N'CONSTRAINT', N'db_contract_plan_item', N'uq_db_contract_item_object', N'防止同一方案重复加入相同差异。'),
    (N'CONSTRAINT', N'db_contract_plan_item', N'ck_db_contract_item_direction', N'限定数据库、Mapper模型或人工判断三种修改方向。'),
    (N'CONSTRAINT', N'db_contract_plan_item', N'ck_db_contract_item_execution', N'限定DDL执行事实状态。'),
    (N'CONSTRAINT', N'db_contract_plan_item', N'ck_db_contract_item_verification', N'限定执行后复验状态。'),
    (N'CONSTRAINT', N'db_contract_plan_item', N'df_db_contract_item_status', N'新方案明细默认等待处理。'),
    (N'CONSTRAINT', N'db_contract_plan_item', N'df_db_contract_item_verify', N'新方案明细默认等待复验。'),
    (N'INDEX', N'db_contract_plan_item', N'ix_db_contract_plan_item_plan', N'支持按方案稳定读取差异、执行和复验状态。');

DECLARE @object_type NVARCHAR(16);
DECLARE @table_name SYSNAME;
DECLARE @object_name SYSNAME;
DECLARE @description NVARCHAR(1000);

DECLARE description_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT object_type, table_name, object_name, description FROM @object_descriptions;

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
