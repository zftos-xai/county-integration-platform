-- 业务域: icd10
-- 脚本类型: PATCH
-- 归属主版本: V1200
-- 补丁序号: 001
-- 变更说明: 依据100-006原始响应的全量结构核查，改按来源疾病ID识别诊断实体，并扩展助记码长度。
-- 需求依据: 西医36647条和中医2653条原始记录的疾病ID均非空且类别内唯一；中医存在538组同编码不同疾病ID；西医助记码最大52字符。
-- 数据边界: 仅补写平台内部来源稳定身份并调整诊断目录元数据；不保存HIS原始报文、患者资料、凭证或机构归属。
-- 时间规则: 不修改任何时间字段；source_created_at继续保留HIS原文，平台观察和批次时间继续保存UTC DATETIME2(3)。
-- 回退方案: 停止新的ICD10同步，使用本次变更前经核验的开发库备份恢复；不得删除已产生的批次事实或手工修改Flyway历史。

SET XACT_ABORT ON;
BEGIN TRANSACTION;
ALTER TABLE dbo.md_icd10_diagnosis ADD
    source_record_key NVARCHAR(64) NULL; -- 平台来源稳定身份，迁移期间允许为空以回填既有目录
GO

BEGIN TRY
    UPDATE dbo.md_icd10_diagnosis
    SET source_record_key = CASE
        WHEN NULLIF(LTRIM(RTRIM(source_disease_id)), N'') IS NULL THEN N'CODE:' + LTRIM(RTRIM(disease_code))
        ELSE N'ID:' + LTRIM(RTRIM(source_disease_id))
    END
    WHERE source_record_key IS NULL;

    IF EXISTS (SELECT 1 FROM dbo.md_icd10_diagnosis
               WHERE source_record_key IS NULL OR LEN(LTRIM(RTRIM(source_record_key))) = 0)
        RAISERROR(N'ICD10来源稳定身份回填失败，终止结构升级。', 16, 1);

    ALTER TABLE dbo.md_icd10_diagnosis ALTER COLUMN source_record_key NVARCHAR(64) NOT NULL; -- 回填完成后禁止空来源身份
    ALTER TABLE dbo.md_icd10_diagnosis ALTER COLUMN mnemonic_code NVARCHAR(64) NULL; -- 保留原始响应最长52字符助记码
    ALTER TABLE dbo.md_icd10_diagnosis DROP CONSTRAINT uq_md_icd10_diagnosis_source;
    ALTER TABLE dbo.md_icd10_diagnosis ADD CONSTRAINT uq_md_icd10_diagnosis_source
        UNIQUE (source_system_code, diagnosis_category, source_record_key); -- 防止同一来源实体重复保存当前事实
    ALTER TABLE dbo.md_icd10_diagnosis ADD CONSTRAINT ck_md_icd10_diagnosis_source_key
        CHECK (LEN(LTRIM(RTRIM(source_record_key))) > 0); -- 禁止写入空白来源稳定身份

    DECLARE @object_descriptions TABLE (object_type NVARCHAR(16) NOT NULL, table_name SYSNAME NOT NULL, object_name SYSNAME NULL, description NVARCHAR(1000) NOT NULL);
    INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
    VALUES
        (N'COLUMN', N'md_icd10_diagnosis', N'source_record_key', N'来源稳定身份（优先疾病ID）'),
        (N'CONSTRAINT', N'md_icd10_diagnosis', N'uq_md_icd10_diagnosis_source', N'防止同一来源诊断实体重复。'),
        (N'CONSTRAINT', N'md_icd10_diagnosis', N'ck_md_icd10_diagnosis_source_key', N'禁止来源稳定身份为空白。');

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

    EXEC sys.sp_updateextendedproperty @name=N'MS_Description', @value=N'来源疾病标识（提供时构成来源稳定身份）',
        @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=N'md_icd10_diagnosis',
        @level2type=N'COLUMN', @level2name=N'source_disease_id';

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
