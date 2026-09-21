-- 业务域: hospital_directory_sync_result
-- 主版本: V0900
-- 脚本类型: MAIN
-- 变更说明: 保存100-003医院综合目录每个目录类型的取得、校验和当前数据更新事实
-- 需求依据: 同步批次总计不能替代科室、医生、病区和床位的分项运行结果；结果页必须可按类型复核
-- 数据边界: 仅保存非敏感的目录类型、数量、受控失败摘要和处理时间；不保存HIS报文、凭证、患者或人员正文
-- 时间规则: finished_at使用SQL Server UTC时间；应用层按用户时区展示
-- 回退方案: 停止读取分项结果并保留表中历史事实；不得删除已完成批次的审计证据

-- 表中文名称: 医院目录同步分项结果表
-- 表用途: 保存100-003单次同步中每一类医院目录的可确认处理结论，供结果展示和后续数量核对使用。
CREATE TABLE md_hospital_directory_sync_result (
    batch_id BIGINT NOT NULL, -- 所属基础数据同步批次主键
    directory_type INT NOT NULL, -- 100-003医院综合目录类型编码
    outcome_status NVARCHAR(32) NOT NULL, -- 单一目录类型处理结论
    returned_count BIGINT NOT NULL CONSTRAINT df_md_directory_sync_result_returned DEFAULT 0, -- HIS实际返回记录数
    duplicate_count BIGINT NOT NULL CONSTRAINT df_md_directory_sync_result_duplicate DEFAULT 0, -- 批次内重复记录数
    invalid_count BIGINT NOT NULL CONSTRAINT df_md_directory_sync_result_invalid DEFAULT 0, -- 字段校验不通过记录数
    conflict_count BIGINT NOT NULL CONSTRAINT df_md_directory_sync_result_conflict DEFAULT 0, -- 同来源编码冲突记录数
    created_count BIGINT NOT NULL CONSTRAINT df_md_directory_sync_result_created DEFAULT 0, -- 新增当前目录记录数
    updated_count BIGINT NOT NULL CONSTRAINT df_md_directory_sync_result_updated DEFAULT 0, -- 更新或恢复有效记录数
    unchanged_count BIGINT NOT NULL CONSTRAINT df_md_directory_sync_result_unchanged DEFAULT 0, -- 与当前记录一致的记录数
    source_missing_count BIGINT NOT NULL CONSTRAINT df_md_directory_sync_result_source_missing DEFAULT 0, -- 完整返回中缺失并标记无效数
    active_count BIGINT NULL, -- 该类型处理后当前有效记录数
    failure_summary NVARCHAR(500) NULL, -- 受控失败摘要，不保存原始响应
    finished_at DATETIME2(3) NOT NULL CONSTRAINT df_md_directory_sync_result_finished DEFAULT SYSUTCDATETIME(), -- 分项结论记录时间（世界协调时）
    CONSTRAINT pk_md_directory_sync_result PRIMARY KEY (batch_id, directory_type), -- 保证每个批次同一目录类型只有一条当前结论
    CONSTRAINT fk_md_directory_sync_result_batch FOREIGN KEY (batch_id) REFERENCES md_sync_batch(id), -- 保证分项结果归属已存在同步批次
    CONSTRAINT ck_md_directory_sync_result_type CHECK (directory_type IN (0, 1, 2, 3)), -- 限定100-003已确认的四类目录
    CONSTRAINT ck_md_directory_sync_result_status CHECK (outcome_status IN (N'COMPLETED', N'FAILED', N'RESULT_UNKNOWN')), -- 限定单类型可确认处理结论
    CONSTRAINT ck_md_directory_sync_result_counts CHECK (returned_count >= 0 AND duplicate_count >= 0 AND invalid_count >= 0 AND conflict_count >= 0 AND created_count >= 0 AND updated_count >= 0 AND unchanged_count >= 0 AND source_missing_count >= 0 AND (active_count IS NULL OR active_count >= 0)), -- 禁止保存负数数量事实
    CONSTRAINT ck_md_directory_sync_result_failure CHECK ((outcome_status = N'COMPLETED' AND failure_summary IS NULL AND active_count IS NOT NULL) OR (outcome_status IN (N'FAILED', N'RESULT_UNKNOWN') AND failure_summary IS NOT NULL AND active_count IS NULL)) -- 保证完成和未完成类型使用一致的证据字段
);

-- 索引用途: 支持按目录类型汇总近期同步分项结果，不扫描全部批次事实。
CREATE INDEX ix_md_directory_sync_result_type_time
    ON md_hospital_directory_sync_result (directory_type, finished_at DESC);

DECLARE @object_descriptions TABLE (
    object_type NVARCHAR(16) NOT NULL,
    table_name SYSNAME NOT NULL,
    object_name SYSNAME NULL,
    description NVARCHAR(1000) NOT NULL
);

INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
VALUES
    (N'TABLE', N'md_hospital_directory_sync_result', NULL, N'医院目录同步分项结果表'),
    (N'COLUMN', N'md_hospital_directory_sync_result', N'batch_id', N'同步批次主键'),
    (N'COLUMN', N'md_hospital_directory_sync_result', N'directory_type', N'医院目录类型'),
    (N'COLUMN', N'md_hospital_directory_sync_result', N'outcome_status', N'处理结论'),
    (N'COLUMN', N'md_hospital_directory_sync_result', N'returned_count', N'实际返回数'),
    (N'COLUMN', N'md_hospital_directory_sync_result', N'duplicate_count', N'重复记录数'),
    (N'COLUMN', N'md_hospital_directory_sync_result', N'invalid_count', N'无效记录数'),
    (N'COLUMN', N'md_hospital_directory_sync_result', N'conflict_count', N'冲突记录数'),
    (N'COLUMN', N'md_hospital_directory_sync_result', N'created_count', N'新增记录数'),
    (N'COLUMN', N'md_hospital_directory_sync_result', N'updated_count', N'更新记录数'),
    (N'COLUMN', N'md_hospital_directory_sync_result', N'unchanged_count', N'未变化记录数'),
    (N'COLUMN', N'md_hospital_directory_sync_result', N'source_missing_count', N'来源缺失数'),
    (N'COLUMN', N'md_hospital_directory_sync_result', N'active_count', N'当前有效数'),
    (N'COLUMN', N'md_hospital_directory_sync_result', N'failure_summary', N'失败摘要'),
    (N'COLUMN', N'md_hospital_directory_sync_result', N'finished_at', N'结论时间'),
    (N'CONSTRAINT', N'md_hospital_directory_sync_result', N'pk_md_directory_sync_result', N'保证每批次每目录类型唯一。'),
    (N'CONSTRAINT', N'md_hospital_directory_sync_result', N'fk_md_directory_sync_result_batch', N'保证分项结果引用已存在批次。'),
    (N'CONSTRAINT', N'md_hospital_directory_sync_result', N'ck_md_directory_sync_result_type', N'限定医院综合目录类型。'),
    (N'CONSTRAINT', N'md_hospital_directory_sync_result', N'ck_md_directory_sync_result_status', N'限定单类型处理结论。'),
    (N'CONSTRAINT', N'md_hospital_directory_sync_result', N'ck_md_directory_sync_result_counts', N'禁止保存负数数量事实。'),
    (N'CONSTRAINT', N'md_hospital_directory_sync_result', N'ck_md_directory_sync_result_failure', N'保证处理结论与证据字段一致。'),
    (N'CONSTRAINT', N'md_hospital_directory_sync_result', N'df_md_directory_sync_result_returned', N'实际返回数默认零。'),
    (N'CONSTRAINT', N'md_hospital_directory_sync_result', N'df_md_directory_sync_result_duplicate', N'重复记录数默认零。'),
    (N'CONSTRAINT', N'md_hospital_directory_sync_result', N'df_md_directory_sync_result_invalid', N'无效记录数默认零。'),
    (N'CONSTRAINT', N'md_hospital_directory_sync_result', N'df_md_directory_sync_result_conflict', N'冲突记录数默认零。'),
    (N'CONSTRAINT', N'md_hospital_directory_sync_result', N'df_md_directory_sync_result_created', N'新增记录数默认零。'),
    (N'CONSTRAINT', N'md_hospital_directory_sync_result', N'df_md_directory_sync_result_updated', N'更新记录数默认零。'),
    (N'CONSTRAINT', N'md_hospital_directory_sync_result', N'df_md_directory_sync_result_unchanged', N'未变化记录数默认零。'),
    (N'CONSTRAINT', N'md_hospital_directory_sync_result', N'df_md_directory_sync_result_source_missing', N'来源缺失数默认零。'),
    (N'CONSTRAINT', N'md_hospital_directory_sync_result', N'df_md_directory_sync_result_finished', N'结论时间默认当前世界协调时。'),
    (N'INDEX', N'md_hospital_directory_sync_result', N'ix_md_directory_sync_result_type_time', N'支持按目录类型和结论时间读取。');

DECLARE @object_type NVARCHAR(16);
DECLARE @table_name SYSNAME;
DECLARE @object_name SYSNAME;
DECLARE @description NVARCHAR(1000);

DECLARE description_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT object_type, table_name, object_name, description
    FROM @object_descriptions;

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
