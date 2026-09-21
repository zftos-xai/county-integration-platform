-- 业务域: medical_directory_sync
-- 主版本: V1000
-- 脚本类型: MAIN
-- 变更说明: 接入100-004医疗目录分页取得、100-005数量核对和当前目录直接对账
-- 需求依据: 药品、诊疗和耗材目录必须先在相同机构、类型和时间范围内核对声明数量，再完整取得并更新当前有效数据
-- 数据边界: 仅保存目录字段、数量事实和受控失败摘要；不保存HIS报文、密码、授权码或患者信息
-- 时间规则: 查询范围和处理时间使用UTC；source_created_at保留HIS原始文本，不猜测来源时区
-- 回退方案: 停止创建MEDICAL_DIRECTORY批次和读取新表；保留已完成同步事实，不物理删除数据

-- 扩展已存在批次类别，保持历史100-003批次约束有效。
ALTER TABLE md_sync_batch DROP CONSTRAINT ck_md_sync_batch_category;
ALTER TABLE md_sync_batch ADD CONSTRAINT ck_md_sync_batch_category
    CHECK (data_category IN ('HOSPITAL_DIRECTORY', 'MEDICAL_DIRECTORY'));

-- 表中文名称: 医疗目录当前数据表
-- 表用途: 保存100-004在完整取得、数量核对和自动校验后得到的当前药品、诊疗和耗材目录事实。
CREATE TABLE md_medical_directory (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_md_medical_directory PRIMARY KEY, -- 当前目录主键
    organization_id BIGINT NOT NULL, -- 平台机构主键
    source_system_code NVARCHAR(64) NOT NULL, -- 来源系统稳定代码
    directory_type NVARCHAR(16) NOT NULL, -- 100-004目录类型：0中药、1西药、2诊疗、3耗材
    source_record_code NVARCHAR(100) NOT NULL, -- HIS目录稳定编码
    source_record_name NVARCHAR(300) NOT NULL, -- HIS目录名称
    mnemonic_code NVARCHAR(100) NULL, -- 助记码
    category_name NVARCHAR(200) NOT NULL, -- 目录类别名称
    unit NVARCHAR(100) NULL, -- 单位
    specification NVARCHAR(500) NULL, -- 规格
    dosage_form NVARCHAR(100) NULL, -- 剂型
    manufacturer_name NVARCHAR(300) NULL, -- 生产厂家
    remark NVARCHAR(500) NULL, -- 非敏感备注
    source_created_at NVARCHAR(100) NOT NULL, -- HIS返回创建时间原文
    package_unit NVARCHAR(100) NULL, -- 包装单位
    conversion_factor NVARCHAR(100) NULL, -- 转换系数原文
    approval_number NVARCHAR(100) NULL, -- 国药准字号
    standard_code NVARCHAR(100) NULL, -- 药品本位码
    package_material NVARCHAR(100) NULL, -- 包装材质
    processing_method NVARCHAR(100) NULL, -- 炮制方法
    region NVARCHAR(100) NULL, -- 地区
    category NVARCHAR(100) NULL, -- 来源类别
    source_enabled_flag NVARCHAR(100) NOT NULL, -- HIS是否启用原始值
    is_valid BIT NOT NULL CONSTRAINT df_md_medical_directory_valid DEFAULT 1, -- 当前有效状态
    invalidated_at DATETIME2(3) NULL, -- 标记无效UTC时间
    invalid_reason NVARCHAR(64) NULL, -- 标记无效原因
    first_seen_batch_id BIGINT NOT NULL, -- 首次完整同步批次
    latest_batch_id BIGINT NOT NULL, -- 最近完整同步批次
    first_seen_at DATETIME2(3) NOT NULL, -- 首次观察UTC时间
    last_seen_at DATETIME2(3) NOT NULL, -- 最近完整观察UTC时间
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_md_medical_directory_created DEFAULT SYSUTCDATETIME(), -- 创建UTC时间
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_md_medical_directory_updated DEFAULT SYSUTCDATETIME(), -- 更新UTC时间
    row_version ROWVERSION, -- 并发版本
    CONSTRAINT fk_md_medical_directory_organization FOREIGN KEY (organization_id) REFERENCES org_organization(id), -- 保证目录归属存在机构
    CONSTRAINT fk_md_medical_directory_first_batch FOREIGN KEY (first_seen_batch_id) REFERENCES md_sync_batch(id), -- 保证首次批次存在
    CONSTRAINT fk_md_medical_directory_latest_batch FOREIGN KEY (latest_batch_id) REFERENCES md_sync_batch(id), -- 保证最近批次存在
    CONSTRAINT uq_md_medical_directory_source UNIQUE (source_system_code, organization_id, directory_type, source_record_code), -- 保证同机构、类型和来源编码唯一
    CONSTRAINT ck_md_medical_directory_type CHECK (directory_type IN ('0', '1', '2', '3')), -- 限定当前已确认的四类100-004目录
    CONSTRAINT ck_md_medical_directory_validity CHECK ((is_valid = 1 AND invalidated_at IS NULL AND invalid_reason IS NULL) OR (is_valid = 0 AND invalidated_at IS NOT NULL AND invalid_reason IS NOT NULL)), -- 保证有效状态与失效事实一致
    CONSTRAINT ck_md_medical_directory_text CHECK (LEN(LTRIM(RTRIM(source_system_code))) > 0 AND LEN(LTRIM(RTRIM(source_record_code))) > 0 AND LEN(LTRIM(RTRIM(source_record_name))) > 0 AND LEN(LTRIM(RTRIM(category_name))) > 0 AND LEN(LTRIM(RTRIM(source_created_at))) > 0 AND LEN(LTRIM(RTRIM(source_enabled_flag))) > 0) -- 禁止空白必填来源字段
);

-- 索引用途: 支持按机构、类型、有效状态和来源编码读取当前医疗目录。
CREATE INDEX ix_md_medical_directory_read
    ON md_medical_directory (organization_id, directory_type, is_valid, source_record_code)
    INCLUDE (source_record_name, category_name, source_enabled_flag, latest_batch_id, last_seen_at);

-- 表中文名称: 医疗目录同步分项结果表
-- 表用途: 保存100-004/100-005在每个医疗目录类型上的数量核对、直接更新和失败事实。
CREATE TABLE md_medical_directory_sync_result (
    batch_id BIGINT NOT NULL, -- 所属基础数据同步批次主键
    directory_type NVARCHAR(16) NOT NULL, -- 医疗目录类型编码
    outcome_status NVARCHAR(32) NOT NULL, -- 单一类型处理结论
    declared_count BIGINT NULL, -- 100-005声明的同范围总数
    returned_count BIGINT NOT NULL CONSTRAINT df_md_medical_sync_result_returned DEFAULT 0, -- 100-004实际返回数
    duplicate_count BIGINT NOT NULL CONSTRAINT df_md_medical_sync_result_duplicate DEFAULT 0, -- 批内完全重复数
    invalid_count BIGINT NOT NULL CONSTRAINT df_md_medical_sync_result_invalid DEFAULT 0, -- 自动校验失败数
    conflict_count BIGINT NOT NULL CONSTRAINT df_md_medical_sync_result_conflict DEFAULT 0, -- 同编码冲突数
    created_count BIGINT NOT NULL CONSTRAINT df_md_medical_sync_result_created DEFAULT 0, -- 新增当前记录数
    updated_count BIGINT NOT NULL CONSTRAINT df_md_medical_sync_result_updated DEFAULT 0, -- 更新或恢复有效数
    unchanged_count BIGINT NOT NULL CONSTRAINT df_md_medical_sync_result_unchanged DEFAULT 0, -- 当前记录未变化数
    source_missing_count BIGINT NOT NULL CONSTRAINT df_md_medical_sync_result_source_missing DEFAULT 0, -- 完整范围未返回并失效数
    active_count BIGINT NULL, -- 本类型处理后当前有效数
    failure_summary NVARCHAR(500) NULL, -- 受控失败摘要
    finished_at DATETIME2(3) NOT NULL CONSTRAINT df_md_medical_sync_result_finished DEFAULT SYSUTCDATETIME(), -- 分项结论时间
    CONSTRAINT pk_md_medical_sync_result PRIMARY KEY (batch_id, directory_type), -- 保证每批次每类型唯一
    CONSTRAINT fk_md_medical_sync_result_batch FOREIGN KEY (batch_id) REFERENCES md_sync_batch(id), -- 保证分项结果归属存在批次
    CONSTRAINT ck_md_medical_sync_result_type CHECK (directory_type IN ('0', '1', '2', '3')), -- 限定当前已确认医疗目录类型
    CONSTRAINT ck_md_medical_sync_result_status CHECK (outcome_status IN (N'COMPLETED', N'FAILED', N'RESULT_UNKNOWN')), -- 限定可确认处理结论
    CONSTRAINT ck_md_medical_sync_result_counts CHECK ((declared_count IS NULL OR declared_count >= 0) AND returned_count >= 0 AND duplicate_count >= 0 AND invalid_count >= 0 AND conflict_count >= 0 AND created_count >= 0 AND updated_count >= 0 AND unchanged_count >= 0 AND source_missing_count >= 0 AND (active_count IS NULL OR active_count >= 0)), -- 禁止负数数量事实
    CONSTRAINT ck_md_medical_sync_result_failure CHECK ((outcome_status = N'COMPLETED' AND failure_summary IS NULL AND active_count IS NOT NULL) OR (outcome_status IN (N'FAILED', N'RESULT_UNKNOWN') AND failure_summary IS NOT NULL AND active_count IS NULL)) -- 保证结论与证据字段匹配
);

-- 索引用途: 支持按目录类型和完成时间读取最近的医疗目录同步事实。
CREATE INDEX ix_md_medical_sync_result_type_time
    ON md_medical_directory_sync_result (directory_type, finished_at DESC);

DECLARE @object_descriptions TABLE (object_type NVARCHAR(16) NOT NULL, table_name SYSNAME NOT NULL, object_name SYSNAME NULL, description NVARCHAR(1000) NOT NULL);
INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
VALUES
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_category', N'限定已形成闭环的基础数据类别。'),
    (N'TABLE', N'md_medical_directory', NULL, N'医疗目录当前数据表'),
    (N'COLUMN', N'md_medical_directory', N'id', N'当前目录主键'), (N'COLUMN', N'md_medical_directory', N'organization_id', N'平台机构主键'), (N'COLUMN', N'md_medical_directory', N'source_system_code', N'来源系统代码'), (N'COLUMN', N'md_medical_directory', N'directory_type', N'医疗目录类型'), (N'COLUMN', N'md_medical_directory', N'source_record_code', N'来源目录编码'), (N'COLUMN', N'md_medical_directory', N'source_record_name', N'来源目录名称'), (N'COLUMN', N'md_medical_directory', N'mnemonic_code', N'助记码'), (N'COLUMN', N'md_medical_directory', N'category_name', N'类别名称'), (N'COLUMN', N'md_medical_directory', N'unit', N'单位'), (N'COLUMN', N'md_medical_directory', N'specification', N'规格'), (N'COLUMN', N'md_medical_directory', N'dosage_form', N'剂型'), (N'COLUMN', N'md_medical_directory', N'manufacturer_name', N'生产厂家'), (N'COLUMN', N'md_medical_directory', N'remark', N'备注'), (N'COLUMN', N'md_medical_directory', N'source_created_at', N'来源创建时间原文'), (N'COLUMN', N'md_medical_directory', N'package_unit', N'包装单位'), (N'COLUMN', N'md_medical_directory', N'conversion_factor', N'转换系数原文'), (N'COLUMN', N'md_medical_directory', N'approval_number', N'国药准字号'), (N'COLUMN', N'md_medical_directory', N'standard_code', N'药品本位码'), (N'COLUMN', N'md_medical_directory', N'package_material', N'包装材质'), (N'COLUMN', N'md_medical_directory', N'processing_method', N'炮制方法'), (N'COLUMN', N'md_medical_directory', N'region', N'地区'), (N'COLUMN', N'md_medical_directory', N'category', N'来源类别'), (N'COLUMN', N'md_medical_directory', N'source_enabled_flag', N'来源启用值'), (N'COLUMN', N'md_medical_directory', N'is_valid', N'平台当前有效状态'), (N'COLUMN', N'md_medical_directory', N'invalidated_at', N'失效时间'), (N'COLUMN', N'md_medical_directory', N'invalid_reason', N'失效原因'), (N'COLUMN', N'md_medical_directory', N'first_seen_batch_id', N'首次同步批次'), (N'COLUMN', N'md_medical_directory', N'latest_batch_id', N'最近同步批次'), (N'COLUMN', N'md_medical_directory', N'first_seen_at', N'首次观察时间'), (N'COLUMN', N'md_medical_directory', N'last_seen_at', N'最近观察时间'), (N'COLUMN', N'md_medical_directory', N'created_at', N'创建时间'), (N'COLUMN', N'md_medical_directory', N'updated_at', N'更新时间'), (N'COLUMN', N'md_medical_directory', N'row_version', N'并发版本'),
    (N'CONSTRAINT', N'md_medical_directory', N'pk_md_medical_directory', N'保证当前目录主键唯一。'), (N'CONSTRAINT', N'md_medical_directory', N'fk_md_medical_directory_organization', N'保证目录归属机构存在。'), (N'CONSTRAINT', N'md_medical_directory', N'fk_md_medical_directory_first_batch', N'保证首次批次存在。'), (N'CONSTRAINT', N'md_medical_directory', N'fk_md_medical_directory_latest_batch', N'保证最近批次存在。'), (N'CONSTRAINT', N'md_medical_directory', N'uq_md_medical_directory_source', N'保证来源目录稳定键唯一。'), (N'CONSTRAINT', N'md_medical_directory', N'ck_md_medical_directory_type', N'限定医疗目录类型。'), (N'CONSTRAINT', N'md_medical_directory', N'ck_md_medical_directory_validity', N'保证有效状态与失效事实一致。'), (N'CONSTRAINT', N'md_medical_directory', N'ck_md_medical_directory_text', N'禁止空白来源必填字段。'), (N'CONSTRAINT', N'md_medical_directory', N'df_md_medical_directory_valid', N'当前目录默认有效。'), (N'CONSTRAINT', N'md_medical_directory', N'df_md_medical_directory_created', N'创建时间默认当前世界协调时。'), (N'CONSTRAINT', N'md_medical_directory', N'df_md_medical_directory_updated', N'更新时间默认当前世界协调时。'), (N'INDEX', N'md_medical_directory', N'ix_md_medical_directory_read', N'支持读取当前医疗目录。'),
    (N'TABLE', N'md_medical_directory_sync_result', NULL, N'医疗目录同步分项结果表'),
    (N'COLUMN', N'md_medical_directory_sync_result', N'batch_id', N'同步批次主键'), (N'COLUMN', N'md_medical_directory_sync_result', N'directory_type', N'医疗目录类型'), (N'COLUMN', N'md_medical_directory_sync_result', N'outcome_status', N'处理结论'), (N'COLUMN', N'md_medical_directory_sync_result', N'declared_count', N'来源声明数量'), (N'COLUMN', N'md_medical_directory_sync_result', N'returned_count', N'实际返回数'), (N'COLUMN', N'md_medical_directory_sync_result', N'duplicate_count', N'重复记录数'), (N'COLUMN', N'md_medical_directory_sync_result', N'invalid_count', N'无效记录数'), (N'COLUMN', N'md_medical_directory_sync_result', N'conflict_count', N'冲突记录数'), (N'COLUMN', N'md_medical_directory_sync_result', N'created_count', N'新增记录数'), (N'COLUMN', N'md_medical_directory_sync_result', N'updated_count', N'更新记录数'), (N'COLUMN', N'md_medical_directory_sync_result', N'unchanged_count', N'未变化记录数'), (N'COLUMN', N'md_medical_directory_sync_result', N'source_missing_count', N'来源缺失数'), (N'COLUMN', N'md_medical_directory_sync_result', N'active_count', N'当前有效数'), (N'COLUMN', N'md_medical_directory_sync_result', N'failure_summary', N'失败摘要'), (N'COLUMN', N'md_medical_directory_sync_result', N'finished_at', N'结论时间'),
    (N'CONSTRAINT', N'md_medical_directory_sync_result', N'pk_md_medical_sync_result', N'保证每批次每类型唯一。'), (N'CONSTRAINT', N'md_medical_directory_sync_result', N'fk_md_medical_sync_result_batch', N'保证结果批次存在。'), (N'CONSTRAINT', N'md_medical_directory_sync_result', N'ck_md_medical_sync_result_type', N'限定医疗目录类型。'), (N'CONSTRAINT', N'md_medical_directory_sync_result', N'ck_md_medical_sync_result_status', N'限定处理结论。'), (N'CONSTRAINT', N'md_medical_directory_sync_result', N'ck_md_medical_sync_result_counts', N'禁止负数数量事实。'), (N'CONSTRAINT', N'md_medical_directory_sync_result', N'ck_md_medical_sync_result_failure', N'保证结论与证据字段匹配。'), (N'CONSTRAINT', N'md_medical_directory_sync_result', N'df_md_medical_sync_result_returned', N'实际返回数默认零。'), (N'CONSTRAINT', N'md_medical_directory_sync_result', N'df_md_medical_sync_result_duplicate', N'重复记录数默认零。'), (N'CONSTRAINT', N'md_medical_directory_sync_result', N'df_md_medical_sync_result_invalid', N'无效记录数默认零。'), (N'CONSTRAINT', N'md_medical_directory_sync_result', N'df_md_medical_sync_result_conflict', N'冲突记录数默认零。'), (N'CONSTRAINT', N'md_medical_directory_sync_result', N'df_md_medical_sync_result_created', N'新增记录数默认零。'), (N'CONSTRAINT', N'md_medical_directory_sync_result', N'df_md_medical_sync_result_updated', N'更新记录数默认零。'), (N'CONSTRAINT', N'md_medical_directory_sync_result', N'df_md_medical_sync_result_unchanged', N'未变化记录数默认零。'), (N'CONSTRAINT', N'md_medical_directory_sync_result', N'df_md_medical_sync_result_source_missing', N'来源缺失数默认零。'), (N'CONSTRAINT', N'md_medical_directory_sync_result', N'df_md_medical_sync_result_finished', N'结论时间默认当前世界协调时。'), (N'INDEX', N'md_medical_directory_sync_result', N'ix_md_medical_sync_result_type_time', N'支持读取最近处理结论。');

DECLARE @object_type NVARCHAR(16), @table_name SYSNAME, @object_name SYSNAME, @description NVARCHAR(1000);
DECLARE description_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT object_type, table_name, object_name, description FROM @object_descriptions;
OPEN description_cursor;
FETCH NEXT FROM description_cursor INTO @object_type, @table_name, @object_name, @description;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF @object_type = N'TABLE'
        EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=@description, @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name;
    ELSE
        EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=@description, @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name, @level2type=@object_type, @level2name=@object_name;
    FETCH NEXT FROM description_cursor INTO @object_type, @table_name, @object_name, @description;
END;
CLOSE description_cursor;
DEALLOCATE description_cursor;
