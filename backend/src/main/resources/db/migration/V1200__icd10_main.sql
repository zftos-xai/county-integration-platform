-- 业务域: icd10
-- 主版本: V1200
-- 脚本类型: MAIN
-- 变更说明: 建立平台公共ICD10当前目录及按诊断类别保存的同步结果。
-- 需求依据: 100-006/100-007提供西医、中医诊断目录；业务确认该目录没有机构归属，来源未提供诊断版本时不创建版本维度。
-- 数据边界: 仅保存诊断基础字段、来源疾病ID、批次追溯和受控数量事实；不保存HIS报文、凭证、患者信息或机构归属。
-- 时间规则: first_seen_at、last_seen_at、created_at、updated_at和finished_at均保存UTC DATETIME2(3)；source_created_at保留HIS原始文本且不推断时区。
-- 回退方案: 停止创建ICD10批次和读取新表；保留已完成批次和目录事实，不物理删除数据，待完成备份与影响评估后按医院流程回退。

-- 表中文名称: 国际疾病分类诊断目录当前数据表
-- 表用途: 保存100-006按西医或中医类别完整取得并通过数量和字段校验后的平台公共当前诊断事实；不承担机构目录、版本管理或患者诊断记录职责。
CREATE TABLE md_icd10_diagnosis (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_md_icd10_diagnosis PRIMARY KEY, -- 平台公共诊断目录内部主键
    source_system_code NVARCHAR(64) NOT NULL, -- 实际提供诊断目录的来源系统稳定代码
    diagnosis_category NVARCHAR(16) NOT NULL, -- 来源疾病类别：0西医诊断、1中医诊断
    disease_code NVARCHAR(50) NOT NULL, -- HIS必填疾病或病种编码；当前公共目录按类别与该编码识别来源事实
    disease_name NVARCHAR(50) NOT NULL, -- HIS必填病种名称
    mnemonic_code NVARCHAR(20) NULL, -- HIS可选助记码，空值表示来源未提供
    remark NVARCHAR(100) NULL, -- HIS可选备注，禁止保存任意原始报文
    source_created_at NVARCHAR(50) NOT NULL, -- HIS必填创建时间原文，来源时区未知时不转换
    source_disease_id NVARCHAR(32) NULL, -- HIS可选疾病ID，仅供来源追溯，不作为当前唯一键
    first_seen_batch_id BIGINT NOT NULL, -- 首次完整观察到该公共诊断的同步批次主键
    latest_batch_id BIGINT NOT NULL, -- 最近一次完整观察到该公共诊断的同步批次主键
    first_seen_at DATETIME2(3) NOT NULL, -- 平台首次观察到该公共诊断的UTC时间
    last_seen_at DATETIME2(3) NOT NULL, -- 平台最近成功观察到该公共诊断的UTC时间
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_md_icd10_diagnosis_created DEFAULT SYSUTCDATETIME(), -- 当前记录创建UTC时间
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_md_icd10_diagnosis_updated DEFAULT SYSUTCDATETIME(), -- 当前记录最近更新UTC时间
    row_version ROWVERSION, -- SQL Server并发版本，不作为业务字段返回
    CONSTRAINT fk_md_icd10_diagnosis_first_batch FOREIGN KEY (first_seen_batch_id) REFERENCES md_sync_batch(id), -- 保证首次观察批次存在
    CONSTRAINT fk_md_icd10_diagnosis_latest_batch FOREIGN KEY (latest_batch_id) REFERENCES md_sync_batch(id), -- 保证最近观察批次存在
    CONSTRAINT uq_md_icd10_diagnosis_source UNIQUE (source_system_code, diagnosis_category, disease_code), -- 防止同一来源类别和疾病编码重复保存当前事实
    CONSTRAINT ck_md_icd10_diagnosis_category CHECK (diagnosis_category IN (N'0', N'1')), -- 限定接口文档确认的西医和中医类别
    CONSTRAINT ck_md_icd10_diagnosis_text CHECK (LEN(LTRIM(RTRIM(source_system_code))) > 0 AND LEN(LTRIM(RTRIM(disease_code))) > 0 AND LEN(LTRIM(RTRIM(disease_name))) > 0 AND LEN(LTRIM(RTRIM(source_created_at))) > 0) -- 禁止空白必填来源字段
);

-- 索引用途: 支持公共目录按类别、名称、编码稳定分页，并覆盖列表中的来源追溯字段。
CREATE INDEX ix_md_icd10_diagnosis_read
    ON md_icd10_diagnosis (diagnosis_category, disease_name, disease_code, id)
    INCLUDE (mnemonic_code, latest_batch_id, last_seen_at);

-- 表中文名称: 国际疾病分类诊断目录同步结果表
-- 表用途: 保存一个公共ICD10批次在西医和中医类别上的数量核对、写入数量及受控失败事实；不保存来源正文。
CREATE TABLE md_icd10_sync_result (
    batch_id BIGINT NOT NULL, -- 所属平台公共ICD10同步批次主键
    diagnosis_category NVARCHAR(16) NOT NULL, -- 本次取得的西医或中医诊断类别
    outcome_status NVARCHAR(32) NOT NULL, -- 当前类别的完成、失败或结果未知结论
    declared_count BIGINT NULL, -- 100-007同范围声明行数，结果未知时为空
    returned_count BIGINT NOT NULL CONSTRAINT df_md_icd10_sync_result_returned DEFAULT 0, -- 100-006实际返回行数
    duplicate_count BIGINT NOT NULL CONSTRAINT df_md_icd10_sync_result_duplicate DEFAULT 0, -- 批内完全重复行数
    invalid_count BIGINT NOT NULL CONSTRAINT df_md_icd10_sync_result_invalid DEFAULT 0, -- 自动字段校验失败行数
    conflict_count BIGINT NOT NULL CONSTRAINT df_md_icd10_sync_result_conflict DEFAULT 0, -- 同类别疾病编码冲突组数
    created_count BIGINT NOT NULL CONSTRAINT df_md_icd10_sync_result_created DEFAULT 0, -- 新增公共诊断数量
    updated_count BIGINT NOT NULL CONSTRAINT df_md_icd10_sync_result_updated DEFAULT 0, -- 更新公共诊断数量
    unchanged_count BIGINT NOT NULL CONSTRAINT df_md_icd10_sync_result_unchanged DEFAULT 0, -- 当前事实无变化数量
    active_count BIGINT NULL, -- 本类别处理后当前公共诊断数量，未完成时为空
    failure_summary NVARCHAR(500) NULL, -- 非完成类别的受控失败摘要，不保存HIS原始错误正文
    finished_at DATETIME2(3) NOT NULL CONSTRAINT df_md_icd10_sync_result_finished DEFAULT SYSUTCDATETIME(), -- 本类别结论完成UTC时间
    CONSTRAINT pk_md_icd10_sync_result PRIMARY KEY (batch_id, diagnosis_category), -- 保证同一批次每个诊断类别仅保存一个最终结论
    CONSTRAINT fk_md_icd10_sync_result_batch FOREIGN KEY (batch_id) REFERENCES md_sync_batch(id), -- 保证结果归属存在的同步批次
    CONSTRAINT ck_md_icd10_sync_result_category CHECK (diagnosis_category IN (N'0', N'1')), -- 限定西医和中医诊断类别
    CONSTRAINT ck_md_icd10_sync_result_status CHECK (outcome_status IN (N'COMPLETED', N'FAILED', N'RESULT_UNKNOWN')), -- 限定可确认的处理结论
    CONSTRAINT ck_md_icd10_sync_result_counts CHECK ((declared_count IS NULL OR declared_count >= 0) AND returned_count >= 0 AND duplicate_count >= 0 AND invalid_count >= 0 AND conflict_count >= 0 AND created_count >= 0 AND updated_count >= 0 AND unchanged_count >= 0 AND (active_count IS NULL OR active_count >= 0)), -- 禁止负数数量事实
    CONSTRAINT ck_md_icd10_sync_result_failure CHECK ((outcome_status = N'COMPLETED' AND failure_summary IS NULL AND active_count IS NOT NULL) OR (outcome_status IN (N'FAILED', N'RESULT_UNKNOWN') AND failure_summary IS NOT NULL AND active_count IS NULL)) -- 保证结论与证据字段匹配
);

-- 索引用途: 支持按诊断类别和最近处理时间读取公共目录同步事实。
CREATE INDEX ix_md_icd10_sync_result_type_time
    ON md_icd10_sync_result (diagnosis_category, finished_at DESC);

DECLARE @object_descriptions TABLE (object_type NVARCHAR(16) NOT NULL, table_name SYSNAME NOT NULL, object_name SYSNAME NULL, description NVARCHAR(1000) NOT NULL);
INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
VALUES
    (N'TABLE', N'md_icd10_diagnosis', NULL, N'国际疾病分类诊断目录当前数据表'),
    (N'COLUMN', N'md_icd10_diagnosis', N'id', N'公共诊断目录主键'), (N'COLUMN', N'md_icd10_diagnosis', N'source_system_code', N'来源系统代码'), (N'COLUMN', N'md_icd10_diagnosis', N'diagnosis_category', N'诊断类别'), (N'COLUMN', N'md_icd10_diagnosis', N'disease_code', N'疾病编码'), (N'COLUMN', N'md_icd10_diagnosis', N'disease_name', N'病种名称'), (N'COLUMN', N'md_icd10_diagnosis', N'mnemonic_code', N'助记码'), (N'COLUMN', N'md_icd10_diagnosis', N'remark', N'非敏感备注（不保存原始报文）'), (N'COLUMN', N'md_icd10_diagnosis', N'source_created_at', N'HIS创建时间原文（来源时区未知，不转换）'), (N'COLUMN', N'md_icd10_diagnosis', N'source_disease_id', N'来源疾病标识'), (N'COLUMN', N'md_icd10_diagnosis', N'first_seen_batch_id', N'首次同步批次'), (N'COLUMN', N'md_icd10_diagnosis', N'latest_batch_id', N'最近同步批次'), (N'COLUMN', N'md_icd10_diagnosis', N'first_seen_at', N'首次观察时间（UTC）'), (N'COLUMN', N'md_icd10_diagnosis', N'last_seen_at', N'最近观察时间（UTC）'), (N'COLUMN', N'md_icd10_diagnosis', N'created_at', N'创建时间（UTC）'), (N'COLUMN', N'md_icd10_diagnosis', N'updated_at', N'更新时间（UTC）'), (N'COLUMN', N'md_icd10_diagnosis', N'row_version', N'并发版本'),
    (N'CONSTRAINT', N'md_icd10_diagnosis', N'pk_md_icd10_diagnosis', N'保证公共诊断目录主键唯一。'), (N'CONSTRAINT', N'md_icd10_diagnosis', N'fk_md_icd10_diagnosis_first_batch', N'保证首次同步批次存在。'), (N'CONSTRAINT', N'md_icd10_diagnosis', N'fk_md_icd10_diagnosis_latest_batch', N'保证最近同步批次存在。'), (N'CONSTRAINT', N'md_icd10_diagnosis', N'uq_md_icd10_diagnosis_source', N'防止同一来源诊断事实重复。'), (N'CONSTRAINT', N'md_icd10_diagnosis', N'ck_md_icd10_diagnosis_category', N'限定诊断类别范围。'), (N'CONSTRAINT', N'md_icd10_diagnosis', N'ck_md_icd10_diagnosis_text', N'禁止空白必填来源字段。'), (N'CONSTRAINT', N'md_icd10_diagnosis', N'df_md_icd10_diagnosis_created', N'默认使用SQL Server当前UTC时间作为创建时间。'), (N'CONSTRAINT', N'md_icd10_diagnosis', N'df_md_icd10_diagnosis_updated', N'默认使用SQL Server当前UTC时间作为更新时间。'), (N'INDEX', N'md_icd10_diagnosis', N'ix_md_icd10_diagnosis_read', N'支持按诊断类别、名称和编码稳定分页读取公共诊断目录。'),
    (N'TABLE', N'md_icd10_sync_result', NULL, N'国际疾病分类诊断目录同步结果表'),
    (N'COLUMN', N'md_icd10_sync_result', N'batch_id', N'同步批次主键'), (N'COLUMN', N'md_icd10_sync_result', N'diagnosis_category', N'诊断类别'), (N'COLUMN', N'md_icd10_sync_result', N'outcome_status', N'处理结论'), (N'COLUMN', N'md_icd10_sync_result', N'declared_count', N'来源声明行数'), (N'COLUMN', N'md_icd10_sync_result', N'returned_count', N'实际返回行数'), (N'COLUMN', N'md_icd10_sync_result', N'duplicate_count', N'重复记录数'), (N'COLUMN', N'md_icd10_sync_result', N'invalid_count', N'无效记录数'), (N'COLUMN', N'md_icd10_sync_result', N'conflict_count', N'冲突记录数'), (N'COLUMN', N'md_icd10_sync_result', N'created_count', N'新增记录数'), (N'COLUMN', N'md_icd10_sync_result', N'updated_count', N'更新记录数'), (N'COLUMN', N'md_icd10_sync_result', N'unchanged_count', N'未变化记录数'), (N'COLUMN', N'md_icd10_sync_result', N'active_count', N'当前有效数量'), (N'COLUMN', N'md_icd10_sync_result', N'failure_summary', N'失败摘要（不保存HIS原始错误正文）'), (N'COLUMN', N'md_icd10_sync_result', N'finished_at', N'结论时间（UTC）'),
    (N'CONSTRAINT', N'md_icd10_sync_result', N'pk_md_icd10_sync_result', N'保证每批次每类别唯一。'), (N'CONSTRAINT', N'md_icd10_sync_result', N'fk_md_icd10_sync_result_batch', N'保证结果批次存在。'), (N'CONSTRAINT', N'md_icd10_sync_result', N'ck_md_icd10_sync_result_category', N'限定诊断类别范围。'), (N'CONSTRAINT', N'md_icd10_sync_result', N'ck_md_icd10_sync_result_status', N'限定处理结论范围。'), (N'CONSTRAINT', N'md_icd10_sync_result', N'ck_md_icd10_sync_result_counts', N'禁止负数数量事实。'), (N'CONSTRAINT', N'md_icd10_sync_result', N'ck_md_icd10_sync_result_failure', N'保证结论与证据字段匹配。'), (N'CONSTRAINT', N'md_icd10_sync_result', N'df_md_icd10_sync_result_returned', N'实际返回行数默认零。'), (N'CONSTRAINT', N'md_icd10_sync_result', N'df_md_icd10_sync_result_duplicate', N'重复记录数默认零。'), (N'CONSTRAINT', N'md_icd10_sync_result', N'df_md_icd10_sync_result_invalid', N'无效记录数默认零。'), (N'CONSTRAINT', N'md_icd10_sync_result', N'df_md_icd10_sync_result_conflict', N'冲突记录数默认零。'), (N'CONSTRAINT', N'md_icd10_sync_result', N'df_md_icd10_sync_result_created', N'新增记录数默认零。'), (N'CONSTRAINT', N'md_icd10_sync_result', N'df_md_icd10_sync_result_updated', N'更新记录数默认零。'), (N'CONSTRAINT', N'md_icd10_sync_result', N'df_md_icd10_sync_result_unchanged', N'未变化记录数默认零。'), (N'CONSTRAINT', N'md_icd10_sync_result', N'df_md_icd10_sync_result_finished', N'默认使用SQL Server当前UTC时间作为结论时间。'), (N'INDEX', N'md_icd10_sync_result', N'ix_md_icd10_sync_result_type_time', N'支持按诊断类别和结论时间读取同步事实。');

DECLARE @object_type NVARCHAR(16), @table_name SYSNAME, @object_name SYSNAME, @description NVARCHAR(1000);
DECLARE description_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT object_type, table_name, object_name, description FROM @object_descriptions;
OPEN description_cursor;
FETCH NEXT FROM description_cursor INTO @object_type, @table_name, @object_name, @description;
WHILE @@FETCH_STATUS = 0
BEGIN
    BEGIN TRY
        IF @object_type = N'TABLE'
            EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=@description,
                @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name;
        ELSE
            EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=@description,
                @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name,
                @level2type=@object_type, @level2name=@object_name;
    END TRY
    BEGIN CATCH
        DECLARE @description_error NVARCHAR(2048) = N'MS_Description写入失败：' + @object_type + N' ' + @table_name + N'.' + ISNULL(@object_name, N'(TABLE)') + N'；' + ERROR_MESSAGE();
        THROW 51000, @description_error, 1;
    END CATCH;
    FETCH NEXT FROM description_cursor INTO @object_type, @table_name, @object_name, @description;
END;
CLOSE description_cursor;
DEALLOCATE description_cursor;
