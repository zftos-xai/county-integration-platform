-- 业务域: master_data
-- 脚本类型: PATCH
-- 归属主版本: V0600
-- 补丁序号: 002
-- 变更说明: 扩展基础数据批次类别，允许ICD10以平台公共范围和指定时间范围执行同步。
-- 需求依据: ICD10目录没有机构归属；100-006和100-007均要求开始时间、结束时间，来源未提供时不使用诊断版本。
-- 数据边界: 只修改批次类别和模式约束；不改写既有医院或医疗目录批次，不将端点配置机构写成ICD10目录归属。
-- 时间规则: ICD10批次查询起止时间继续使用UTC DATETIME2(3)保存，发送HIS前由业务层转换为医院查询时区。
-- 回退方案: 停用ICD10批次入口和读取路径；保留已有批次审计事实，完成备份和影响评估后恢复原类别、模式约束。

ALTER TABLE dbo.md_sync_batch DROP CONSTRAINT ck_md_sync_batch_category;
ALTER TABLE dbo.md_sync_batch DROP CONSTRAINT ck_md_sync_batch_mode_scope;
GO

ALTER TABLE dbo.md_sync_batch WITH CHECK ADD CONSTRAINT ck_md_sync_batch_category
    CHECK (data_category IN (N'HOSPITAL_DIRECTORY', N'MEDICAL_DIRECTORY', N'ICD10_DIAGNOSIS')); -- 限定已形成闭环的机构目录和公共诊断目录类别
ALTER TABLE dbo.md_sync_batch WITH CHECK ADD CONSTRAINT ck_md_sync_batch_mode_scope
    CHECK (
        (data_category = N'HOSPITAL_DIRECTORY' AND scope_type = N'ORGANIZATION' AND sync_mode = N'NOT_APPLICABLE' AND query_started_at IS NULL AND query_ended_at IS NULL AND full_rule_evidence IS NULL AND diagnosis_category IS NULL AND diagnosis_version IS NULL)
        OR (data_category = N'MEDICAL_DIRECTORY' AND scope_type = N'ORGANIZATION' AND sync_mode = N'TIME_RANGE' AND query_started_at IS NOT NULL AND query_ended_at IS NOT NULL AND full_rule_evidence IS NULL AND diagnosis_category IS NULL AND diagnosis_version IS NULL)
        OR (data_category = N'MEDICAL_DIRECTORY' AND scope_type = N'ORGANIZATION' AND sync_mode = N'FULL' AND query_started_at IS NOT NULL AND query_ended_at IS NOT NULL AND full_rule_evidence IS NOT NULL AND diagnosis_category IS NULL AND diagnosis_version IS NULL)
        OR (data_category = N'ICD10_DIAGNOSIS' AND scope_type = N'PLATFORM' AND sync_mode = N'TIME_RANGE' AND query_started_at IS NOT NULL AND query_ended_at IS NOT NULL AND full_rule_evidence IS NULL AND diagnosis_category IS NULL AND diagnosis_version IS NULL)
    ); -- 保证ICD10固定为公共无版本的指定时间范围批次，其他类别保持既有范围规则
GO

DECLARE @object_descriptions TABLE (object_type NVARCHAR(16), table_name SYSNAME, object_name SYSNAME, description NVARCHAR(1000));
INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
VALUES
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_category', N'限定已形成闭环的基础数据类别。'),
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_mode_scope', N'保证类别范围与查询模式一致。');

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
