-- 业务域: master_data
-- 脚本类型: PATCH
-- 归属主版本: V0600
-- 补丁序号: 001
-- 变更说明: 为已存在批次增加同步模式、来源端点快照和全量依据，并收紧活动批次占用范围
-- 需求依据: 医疗目录须明确选择时间范围或全量同步，批次须绑定创建时已验证来源端点
-- 数据边界: 保留历史批次与审计；历史医疗批次按其已有时间范围登记为TIME_RANGE，不推定为全量
-- 时间规则: 既有查询起止时间继续保存UTC，精度为毫秒；本补丁不改写时间值
-- 回退方案: 停用依赖同步模式的新入口，恢复旧活动范围索引并移除新约束和字段；先导出新增字段证据

IF EXISTS (
    SELECT 1 FROM dbo.md_sync_batch
    WHERE data_category = N'MEDICAL_DIRECTORY' AND (query_started_at IS NULL OR query_ended_at IS NULL)
)
    THROW 51001, N'历史医疗目录批次缺少查询时间，不能自动判定同步模式', 1;
GO

ALTER TABLE dbo.md_sync_batch ADD
    sync_mode NVARCHAR(20) NOT NULL CONSTRAINT df_md_sync_batch_mode DEFAULT N'NOT_APPLICABLE' WITH VALUES, -- 医院目录不适用；已有医疗批次按下方原时间范围回填
    source_endpoint_id BIGINT NULL, -- 批次创建时绑定的来源端点；历史批次未知时为空
    source_endpoint_version VARBINARY(8) NULL, -- 批次创建时的端点并发版本；历史批次未知时为空
    full_rule_evidence NVARCHAR(500) NULL -- 全量覆盖口径的非敏感依据；历史时间范围批次为空
;
GO

UPDATE dbo.md_sync_batch SET sync_mode = N'TIME_RANGE'
WHERE data_category = N'MEDICAL_DIRECTORY';
GO

ALTER TABLE dbo.md_sync_batch WITH CHECK ADD CONSTRAINT fk_md_sync_batch_endpoint
    FOREIGN KEY (source_endpoint_id) REFERENCES dbo.sys_external_endpoint(id); -- 新批次绑定的端点必须存在
ALTER TABLE dbo.md_sync_batch WITH CHECK ADD CONSTRAINT ck_md_sync_batch_mode
    CHECK (sync_mode IN (N'NOT_APPLICABLE', N'TIME_RANGE', N'FULL')); -- 不接受含义不明的模式
ALTER TABLE dbo.md_sync_batch WITH CHECK ADD CONSTRAINT ck_md_sync_batch_mode_scope
    CHECK (
        (data_category = N'HOSPITAL_DIRECTORY' AND sync_mode = N'NOT_APPLICABLE' AND query_started_at IS NULL AND query_ended_at IS NULL AND full_rule_evidence IS NULL)
        OR (data_category = N'MEDICAL_DIRECTORY' AND sync_mode = N'TIME_RANGE' AND query_started_at IS NOT NULL AND query_ended_at IS NOT NULL AND full_rule_evidence IS NULL)
        OR (data_category = N'MEDICAL_DIRECTORY' AND sync_mode = N'FULL' AND query_started_at IS NOT NULL AND query_ended_at IS NOT NULL AND full_rule_evidence IS NOT NULL)
    ); -- 模式、时间范围和全量依据必须一致
GO

-- 索引用途: 同一来源、机构和目录只允许一个活动批次写入，避免不同查询条件并发覆盖当前数据。
DROP INDEX ux_md_sync_batch_active_scope ON dbo.md_sync_batch;
CREATE UNIQUE INDEX ux_md_sync_batch_active_scope
    ON dbo.md_sync_batch (source_system_code, scope_type, organization_id, data_category)
    WHERE is_active = 1;
GO

DECLARE @object_descriptions TABLE (object_type NVARCHAR(16), table_name SYSNAME, object_name SYSNAME, description NVARCHAR(1000));
INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
VALUES
    (N'COLUMN', N'md_sync_batch', N'sync_mode', N'同步模式'),
    (N'COLUMN', N'md_sync_batch', N'source_endpoint_id', N'来源端点主键'),
    (N'COLUMN', N'md_sync_batch', N'source_endpoint_version', N'来源端点并发版本'),
    (N'COLUMN', N'md_sync_batch', N'full_rule_evidence', N'全量查询确认依据'),
    (N'CONSTRAINT', N'md_sync_batch', N'df_md_sync_batch_mode', N'医院目录默认不使用时间范围。'),
    (N'CONSTRAINT', N'md_sync_batch', N'fk_md_sync_batch_endpoint', N'保证批次绑定的来源服务端点存在。'),
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_mode', N'限定可识别的同步模式。'),
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_mode_scope', N'保证模式与时间范围及全量依据一致。'),
    (N'INDEX', N'md_sync_batch', N'ux_md_sync_batch_active_scope', N'阻止同一来源、机构和目录的多个活动批次并发写入。');

DECLARE @object_type NVARCHAR(16), @table_name SYSNAME, @object_name SYSNAME, @description NVARCHAR(1000);
DECLARE description_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT object_type, table_name, object_name, description FROM @object_descriptions;
OPEN description_cursor;
FETCH NEXT FROM description_cursor INTO @object_type, @table_name, @object_name, @description;
WHILE @@FETCH_STATUS = 0
BEGIN
    EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=@description,
        @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name,
        @level2type=@object_type, @level2name=@object_name;
    FETCH NEXT FROM description_cursor INTO @object_type, @table_name, @object_name, @description;
END;
CLOSE description_cursor;
DEALLOCATE description_cursor;
