-- 业务域: master_data
-- 主版本: V0600
-- 脚本类型: MAIN
-- 变更说明: 建立100-003医院综合目录直接对账、自动校验和当前目录持久化底座
-- 需求依据: 基础数据业务详细计划BD-01、2026-09-19基础数据正式实现拆分
-- 数据边界: 仅保存批次事实、自动校验后的非敏感目录和接口来源确认事实；不保存凭证、SOAP原文、完整JSON、患者数据或未经确认的业务目录字段
-- 时间规则: 查询范围、处理、核查、验证、创建和更新时间均保存UTC时间，应用层负责带偏移时间转换
-- 回退方案: 主版本进入共享环境前可按外键逆序删除本脚本对象；进入共享环境后只能新增归属V0600的版本化补丁

-- 机构接口自动校验事实：本主版本尚未进入共享环境，随基础数据闭环一并收敛到唯一主版本。
-- 100-008的来源机构ID属于接口配置事实，不属于人工“机构映射”业务。
ALTER TABLE sys_external_endpoint ADD
    organization_query_name NVARCHAR(100) NULL, -- 100-008查询使用的医院名称：由机构配置人员按HIS资料填写
    source_organization_id NVARCHAR(128) NULL, -- 已确认来源机构ID：仅100-008返回唯一结果后写入
    source_organization_name NVARCHAR(200) NULL, -- 已确认来源机构名称：仅用于可理解回显和追溯
    verification_status NVARCHAR(32) NOT NULL CONSTRAINT df_sys_external_endpoint_verification_status DEFAULT N'NOT_VERIFIED', -- 自动校验结果：未校验、已确认、失败或结果未知
    verified_at DATETIME2(3) NULL, -- 最近一次100-008校验完成UTC时间
    verification_failure_summary NVARCHAR(500) NULL; -- 不包含地址、凭证和原始报文的失败说明

-- SQL Server 2012须在下一批次编译引用新增字段的约束。
GO

ALTER TABLE sys_external_endpoint ADD CONSTRAINT ck_sys_external_endpoint_verification_status
    CHECK (verification_status IN (N'NOT_VERIFIED', N'VERIFIED', N'FAILED', N'RESULT_UNKNOWN'));

ALTER TABLE sys_external_endpoint ADD CONSTRAINT ck_sys_external_endpoint_verification_fact
    CHECK ((verification_status = N'VERIFIED' AND source_organization_id IS NOT NULL
            AND source_organization_name IS NOT NULL AND verified_at IS NOT NULL AND is_enabled = 1)
        OR (verification_status <> N'VERIFIED' AND is_enabled = 0));

-- 表中文名称: 基础数据同步批次表
-- 表用途: 保存一次范围明确的基础数据同步从创建到完成的业务事实；不保存来源接口完整请求、响应正文或授权凭证
CREATE TABLE md_sync_batch (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_md_sync_batch PRIMARY KEY, -- 批次主键：平台内部自增主键
    batch_no NVARCHAR(64) NOT NULL, -- 批次号：面向业务追踪的稳定批次编号
    request_key NVARCHAR(64) NOT NULL, -- 请求标识：一次用户操作的幂等标识，用于超时后回读
    source_system_code NVARCHAR(64) NOT NULL, -- 来源系统代码：实际提供本批数据的外部系统稳定代码
    scope_type NVARCHAR(16) NOT NULL, -- 范围类型：ORGANIZATION表示机构范围，PLATFORM表示平台公共范围
    organization_id BIGINT NULL, -- 平台机构主键：机构范围必填；平台公共范围为空
    environment_code NVARCHAR(16) NOT NULL, -- 运行环境：本批调用外部接口时使用的明确配置环境
    data_category NVARCHAR(32) NOT NULL, -- 数据类别：首批已确认的机构、目录或诊断类别代码
    sync_mode NVARCHAR(20) NOT NULL CONSTRAINT df_md_sync_batch_mode DEFAULT N'NOT_APPLICABLE', -- 同步模式：医院目录不适用；医疗目录明确为全量或指定时间范围
    source_type NVARCHAR(16) NULL, -- 来源目录类型：100-003或100-004的目录类型；接口不需要时为空
    source_organization_id NVARCHAR(128) NULL, -- 来源机构标识：从已验证100-008映射取得并固化到批次，公共目录为空
    source_endpoint_id BIGINT NULL, -- 来源端点主键：创建时绑定的已验证服务端点；迁入旧批次可为空
    source_endpoint_version VARBINARY(8) NULL, -- 来源端点版本：创建时的八字节行版本；迁入旧批次可为空
    full_rule_evidence NVARCHAR(500) NULL, -- 全量依据：来源方确认时间字段和完整覆盖口径的非敏感摘要；非全量为空
    query_started_at DATETIME2(3) NULL, -- 查询范围开始时间：来源查询条件的UTC时间；接口不需要时为空
    query_ended_at DATETIME2(3) NULL, -- 查询范围结束时间：来源查询条件的UTC时间；接口不需要时为空
    diagnosis_category NVARCHAR(32) NULL, -- 诊断疾病类别：ICD10查询条件；其他数据类别为空
    diagnosis_version NVARCHAR(64) NULL, -- 诊断版本：ICD10查询条件；其他数据类别为空
    data_trade_code NVARCHAR(64) NOT NULL, -- 数据交易码：本批取得数据使用的HIS交易码
    count_trade_code NVARCHAR(64) NULL, -- 数量交易码：来源提供独立数量查询时使用；否则为空
    batch_status NVARCHAR(32) NOT NULL, -- 同步状态：创建、取得、完成、部分异常或已确认异常状态
    is_active BIT NOT NULL CONSTRAINT df_md_sync_batch_active DEFAULT 1, -- 活动标志：非终态批次为1，用于阻止同范围并发执行
    declared_count BIGINT NULL, -- 来源声明数：数量接口返回的同范围记录数；来源不提供时为空
    returned_count BIGINT NOT NULL CONSTRAINT df_md_sync_batch_returned DEFAULT 0, -- 实际取得数：本批各次成功返回的记录总数
    duplicate_count BIGINT NOT NULL CONSTRAINT df_md_sync_batch_duplicate DEFAULT 0, -- 重复数：批次范围内按稳定来源键识别的重复记录数
    invalid_count BIGINT NOT NULL CONSTRAINT df_md_sync_batch_invalid DEFAULT 0, -- 无效数：必填字段或受控代码校验失败的记录数
    conflict_count BIGINT NOT NULL CONSTRAINT df_md_sync_batch_conflict DEFAULT 0, -- 冲突数：相同来源键对应互相冲突事实的记录数
    created_count BIGINT NOT NULL CONSTRAINT df_md_sync_batch_created_count DEFAULT 0, -- 新增数：直接同步时新写入当前目录的记录数
    updated_count BIGINT NOT NULL CONSTRAINT df_md_sync_batch_updated_count DEFAULT 0, -- 更新数：直接同步时更新或恢复有效的记录数
    unchanged_count BIGINT NOT NULL CONSTRAINT df_md_sync_batch_unchanged DEFAULT 0, -- 未变化数：与当前目录一致的记录数
    source_missing_count BIGINT NOT NULL CONSTRAINT df_md_sync_batch_source_missing DEFAULT 0, -- 来源缺失数：完整来源范围未返回并标记无效的记录数
    active_count BIGINT NULL, -- 有效数：本次已完成目录对账后平台当前有效记录数；未完成时为空
    started_at DATETIME2(3) NULL, -- 开始时间：批次实际开始处理的UTC时间；尚未开始时为空
    finished_at DATETIME2(3) NULL, -- 结束时间：批次进入当前终态的UTC时间；活动批次为空
    completed_at DATETIME2(3) NULL, -- 完成时间：本次直接同步与对账完成的UTC时间；未完成时为空
    failure_code NVARCHAR(64) NULL, -- 失败代码：可分类处理且不包含内部异常栈的稳定代码
    failure_summary NVARCHAR(500) NULL, -- 失败摘要：面向运维人员且不包含凭证、地址和业务正文的说明
    created_by NVARCHAR(64) NOT NULL, -- 创建人标识：发起人稳定用户标识或调度器标识快照
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_md_sync_batch_created DEFAULT SYSUTCDATETIME(), -- 创建时间：批次记录创建UTC时间
    updated_by NVARCHAR(64) NOT NULL, -- 更新人标识：最后改变批次事实的稳定用户或服务标识
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_md_sync_batch_updated DEFAULT SYSUTCDATETIME(), -- 更新时间：批次事实最后更新UTC时间
    row_version ROWVERSION, -- 并发版本：SQL Server并发版本，用于防止无感知覆盖
    CONSTRAINT uq_md_sync_batch_no UNIQUE (batch_no), -- 保证面向业务追踪的批次号唯一
    CONSTRAINT uq_md_sync_batch_request UNIQUE (request_key), -- 保证一次用户操作只创建一个批次
    CONSTRAINT fk_md_sync_batch_organization FOREIGN KEY (organization_id) REFERENCES org_organization(id), -- 保证机构范围引用已存在的平台机构
    CONSTRAINT fk_md_sync_batch_endpoint FOREIGN KEY (source_endpoint_id) REFERENCES sys_external_endpoint(id), -- 保证新批次绑定的来源端点存在
    CONSTRAINT ck_md_sync_batch_scope CHECK ((scope_type = 'ORGANIZATION' AND organization_id IS NOT NULL AND source_organization_id IS NOT NULL) OR (scope_type = 'PLATFORM' AND organization_id IS NULL AND source_organization_id IS NULL)), -- 保证机构范围批次固化已验证来源机构标识，公共范围不混用机构信息
    CONSTRAINT ck_md_sync_batch_environment CHECK (environment_code IN ('DEVELOPMENT', 'TEST', 'PRODUCTION')), -- 限定平台支持的运行环境
    CONSTRAINT ck_md_sync_batch_category CHECK (data_category IN ('HOSPITAL_DIRECTORY')), -- 当前只允许已经形成完整闭环的医院综合目录
    CONSTRAINT ck_md_sync_batch_mode CHECK (sync_mode IN (N'NOT_APPLICABLE', N'TIME_RANGE', N'FULL')), -- 只允许已定义的同步模式
    CONSTRAINT ck_md_sync_batch_mode_scope CHECK ((data_category = N'HOSPITAL_DIRECTORY' AND sync_mode = N'NOT_APPLICABLE' AND query_started_at IS NULL AND query_ended_at IS NULL AND full_rule_evidence IS NULL) OR (data_category = N'MEDICAL_DIRECTORY' AND sync_mode = N'TIME_RANGE' AND query_started_at IS NOT NULL AND query_ended_at IS NOT NULL AND full_rule_evidence IS NULL) OR (data_category = N'MEDICAL_DIRECTORY' AND sync_mode = N'FULL' AND query_started_at IS NOT NULL AND query_ended_at IS NOT NULL AND full_rule_evidence IS NOT NULL)), -- 模式决定实际范围及规则证据
    CONSTRAINT ck_md_sync_batch_status CHECK (batch_status IN ('CREATED', 'FETCHING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'COMPLETED_WITH_UNKNOWN', 'FAILED', 'RESULT_UNKNOWN')), -- 限定自动同步状态，不设置人工发布或核查状态
    CONSTRAINT ck_md_sync_batch_active_status CHECK ((batch_status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS', 'COMPLETED_WITH_UNKNOWN', 'FAILED') AND is_active = 0) OR (batch_status IN ('CREATED', 'FETCHING', 'RESULT_UNKNOWN') AND is_active = 1)), -- 保证活动标志与终态保持一致
    CONSTRAINT ck_md_sync_batch_query_time CHECK (query_ended_at IS NULL OR query_started_at IS NULL OR query_ended_at >= query_started_at), -- 禁止查询范围结束早于开始
    CONSTRAINT ck_md_sync_batch_processing_time CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at), -- 禁止批次处理结束早于开始
    CONSTRAINT ck_md_sync_batch_completion_state CHECK ((batch_status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS', 'COMPLETED_WITH_UNKNOWN') AND completed_at IS NOT NULL AND active_count IS NOT NULL) OR (batch_status NOT IN ('COMPLETED', 'COMPLETED_WITH_ERRORS', 'COMPLETED_WITH_UNKNOWN') AND completed_at IS NULL AND active_count IS NULL)), -- 保证完成运行保存当前有效数和完成时间
    CONSTRAINT ck_md_sync_batch_counts CHECK ((declared_count IS NULL OR declared_count >= 0) AND returned_count >= 0 AND duplicate_count >= 0 AND invalid_count >= 0 AND conflict_count >= 0 AND created_count >= 0 AND updated_count >= 0 AND unchanged_count >= 0 AND source_missing_count >= 0 AND (active_count IS NULL OR active_count >= 0)), -- 禁止保存负数数量事实
    CONSTRAINT ck_md_sync_batch_text CHECK (LEN(LTRIM(RTRIM(batch_no))) > 0 AND LEN(LTRIM(RTRIM(request_key))) >= 20 AND LEN(LTRIM(RTRIM(source_system_code))) > 0 AND LEN(LTRIM(RTRIM(data_trade_code))) > 0) -- 禁止保存空白关键标识并要求请求标识具有足够随机长度
);

-- 索引用途: 阻止不同查询时间或接口环境的活动批次同时写同一来源、机构和目录
CREATE UNIQUE INDEX ux_md_sync_batch_active_scope
    ON md_sync_batch (
        source_system_code, scope_type, organization_id, data_category
    )
    WHERE is_active = 1;

-- 索引用途: 支持管理端按机构、类别、状态和开始时间倒序分页查询同步批次
CREATE INDEX ix_md_sync_batch_management_query
    ON md_sync_batch (organization_id, data_category, batch_status, started_at, id)
    INCLUDE (batch_no, scope_type, returned_count, invalid_count, conflict_count, completed_at);

-- 表中文名称: 医院综合目录当前数据表
-- 表用途: 保存100-003按目录类型完整对账后的科室、医生、病区和床位当前事实；没有人工发布或版本切换
CREATE TABLE md_hospital_directory (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_md_hospital_directory PRIMARY KEY, -- 正式目录主键
    organization_id BIGINT NOT NULL, -- 平台机构主键
    source_system_code NVARCHAR(64) NOT NULL, -- 来源系统稳定代码
    directory_type NVARCHAR(16) NOT NULL, -- 100-003目录类型，0科室、1医生、2病区、3床位
    source_record_code NVARCHAR(50) NOT NULL, -- 来源目录稳定编码
    source_record_name NVARCHAR(50) NOT NULL, -- 来源目录名称
    mnemonic_code NVARCHAR(20) NULL, -- 可选助记码
    category_name NVARCHAR(20) NULL, -- 可选目录类别
    remark NVARCHAR(100) NULL, -- 可选非敏感备注
    department_code NVARCHAR(50) NULL, -- 医生所属科室编码
    department_name NVARCHAR(50) NULL, -- 医生所属科室名称
    ward_name NVARCHAR(50) NULL, -- 床位所属病区名称
    source_organization_code NVARCHAR(50) NULL, -- 来源返回的机构编码
    source_enabled_flag NVARCHAR(50) NULL, -- HIS“是否启用”原始值：仅保存来源事实，不等同平台有效状态
    is_valid BIT NOT NULL CONSTRAINT df_md_hospital_directory_valid DEFAULT 1, -- 平台当前有效状态：完整来源范围未返回时标记为0
    invalidated_at DATETIME2(3) NULL, -- 标记无效UTC时间：仅is_valid为0时存在
    invalid_reason NVARCHAR(64) NULL, -- 无效原因：仅is_valid为0时存在，例如HIS_NOT_RETURNED
    first_seen_batch_id BIGINT NOT NULL, -- 首次成功同步批次
    latest_batch_id BIGINT NOT NULL, -- 当前版本来源批次
    first_seen_at DATETIME2(3) NOT NULL, -- 首次观察UTC时间
    last_seen_at DATETIME2(3) NOT NULL, -- 最近成功同步UTC时间
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_md_hospital_directory_created DEFAULT SYSUTCDATETIME(), -- 创建UTC时间
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_md_hospital_directory_updated DEFAULT SYSUTCDATETIME(), -- 当前版本更新时间
    row_version ROWVERSION, -- 并发版本
    CONSTRAINT fk_md_hospital_directory_organization FOREIGN KEY (organization_id) REFERENCES org_organization(id), -- 目录归属受控机构
    CONSTRAINT fk_md_hospital_directory_first_batch FOREIGN KEY (first_seen_batch_id) REFERENCES md_sync_batch(id), -- 首次批次存在
    CONSTRAINT fk_md_hospital_directory_latest_batch FOREIGN KEY (latest_batch_id) REFERENCES md_sync_batch(id), -- 当前批次存在
    CONSTRAINT uq_md_hospital_directory_source UNIQUE (source_system_code, organization_id, directory_type, source_record_code), -- 同机构同类型来源编码唯一
    CONSTRAINT ck_md_hospital_directory_type CHECK (directory_type IN ('0', '1', '2', '3')), -- 只保存已确认的100-003四类目录
    CONSTRAINT ck_md_hospital_directory_validity CHECK ((is_valid = 1 AND invalidated_at IS NULL AND invalid_reason IS NULL) OR (is_valid = 0 AND invalidated_at IS NOT NULL AND invalid_reason IS NOT NULL)), -- 保证有效状态与无效事实一致
    CONSTRAINT ck_md_hospital_directory_text CHECK (LEN(LTRIM(RTRIM(source_system_code))) > 0 AND LEN(LTRIM(RTRIM(source_record_code))) > 0 AND LEN(LTRIM(RTRIM(source_record_name))) > 0) -- 禁止空白关键字段
);

-- 索引用途: 支持按机构、目录类型和来源编码读取当前有效目录
CREATE INDEX ix_md_hospital_directory_read
    ON md_hospital_directory (organization_id, directory_type, is_valid, source_record_code)
    INCLUDE (source_record_name, source_enabled_flag, department_code, ward_name, latest_batch_id, last_seen_at);

-- 表中文名称: 医院综合目录当前关系表
-- 表用途: 保存最近一次完整对账的100-003目录隶属关系，供目录读取与业务关联使用
CREATE TABLE md_hospital_directory_relation (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_md_hospital_directory_relation PRIMARY KEY, -- 正式关系主键
    organization_id BIGINT NOT NULL, -- 平台机构主键
    source_system_code NVARCHAR(64) NOT NULL, -- 来源系统稳定代码
    relation_type NVARCHAR(32) NOT NULL, -- 关系类型
    source_record_code NVARCHAR(50) NOT NULL, -- 关系起点的来源目录编码
    target_record_code NVARCHAR(50) NOT NULL, -- 关系终点的来源目录编码
    latest_batch_id BIGINT NOT NULL, -- 当前关系来源批次
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_md_directory_relation_created DEFAULT SYSUTCDATETIME(), -- 创建UTC时间
    CONSTRAINT fk_md_directory_relation_organization FOREIGN KEY (organization_id) REFERENCES org_organization(id), -- 关系归属机构存在
    CONSTRAINT fk_md_directory_relation_batch FOREIGN KEY (latest_batch_id) REFERENCES md_sync_batch(id), -- 关系来源批次存在
    CONSTRAINT uq_md_directory_relation_source UNIQUE (source_system_code, organization_id, relation_type, source_record_code, target_record_code), -- 同机构关系唯一
    CONSTRAINT ck_md_directory_relation_type CHECK (relation_type IN ('DOCTOR_WARD', 'WARD_DEPARTMENT', 'BED_WARD')), -- 限定已确认关系类型
    CONSTRAINT ck_md_directory_relation_text CHECK (LEN(LTRIM(RTRIM(source_system_code))) > 0 AND LEN(LTRIM(RTRIM(source_record_code))) > 0 AND LEN(LTRIM(RTRIM(target_record_code))) > 0) -- 禁止空白关系标识
);

-- 索引用途: 支持由目录记录查询其全部隶属关系
CREATE INDEX ix_md_directory_relation_read
    ON md_hospital_directory_relation (organization_id, relation_type, source_record_code)
    INCLUDE (target_record_code, latest_batch_id);

IF NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = N'master-data:read')
    INSERT INTO sys_permission (permission_code, permission_name) VALUES (N'master-data:read', N'查询基础数据');
IF NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = N'master-data:sync')
    INSERT INTO sys_permission (permission_code, permission_name) VALUES (N'master-data:sync', N'发起基础数据同步');
INSERT INTO sys_role_permission (role_id, permission_code, granted_by)
SELECT role_row.id, permission_row.permission_code, N'migration-V0600'
FROM sys_role role_row
CROSS JOIN sys_permission permission_row
WHERE role_row.role_code = N'PLATFORM_ADMIN'
  AND permission_row.permission_code IN (N'master-data:read', N'master-data:sync')
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission existing_permission
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
    (N'TABLE', N'md_sync_batch', NULL, N'基础数据同步批次表'),
    (N'COLUMN', N'md_sync_batch', N'id', N'批次主键'),
    (N'COLUMN', N'md_sync_batch', N'batch_no', N'批次号'),
    (N'COLUMN', N'md_sync_batch', N'request_key', N'请求标识'),
    (N'COLUMN', N'md_sync_batch', N'source_system_code', N'来源系统代码'),
    (N'COLUMN', N'md_sync_batch', N'scope_type', N'范围类型'),
    (N'COLUMN', N'md_sync_batch', N'organization_id', N'平台机构主键'),
    (N'COLUMN', N'md_sync_batch', N'environment_code', N'来源接口运行环境'),
    (N'COLUMN', N'md_sync_batch', N'data_category', N'数据类别'),
    (N'COLUMN', N'md_sync_batch', N'sync_mode', N'同步模式'),
    (N'COLUMN', N'md_sync_batch', N'source_type', N'来源目录类型'),
    (N'COLUMN', N'md_sync_batch', N'source_organization_id', N'已验证来源机构标识快照'),
    (N'COLUMN', N'md_sync_batch', N'source_endpoint_id', N'来源端点主键'),
    (N'COLUMN', N'md_sync_batch', N'source_endpoint_version', N'来源端点并发版本'),
    (N'COLUMN', N'md_sync_batch', N'full_rule_evidence', N'全量查询确认依据'),
    (N'COLUMN', N'md_sync_batch', N'query_started_at', N'查询范围开始时间（世界协调时）'),
    (N'COLUMN', N'md_sync_batch', N'query_ended_at', N'查询范围结束时间（世界协调时）'),
    (N'COLUMN', N'md_sync_batch', N'diagnosis_category', N'诊断疾病类别'),
    (N'COLUMN', N'md_sync_batch', N'diagnosis_version', N'诊断版本'),
    (N'COLUMN', N'md_sync_batch', N'data_trade_code', N'数据交易码'),
    (N'COLUMN', N'md_sync_batch', N'count_trade_code', N'数量交易码'),
    (N'COLUMN', N'md_sync_batch', N'batch_status', N'批次状态'),
    (N'COLUMN', N'md_sync_batch', N'is_active', N'活动标志'),
    (N'COLUMN', N'md_sync_batch', N'declared_count', N'来源声明数'),
    (N'COLUMN', N'md_sync_batch', N'returned_count', N'实际取得数'),
    (N'COLUMN', N'md_sync_batch', N'duplicate_count', N'重复数'),
    (N'COLUMN', N'md_sync_batch', N'invalid_count', N'无效数'),
    (N'COLUMN', N'md_sync_batch', N'conflict_count', N'冲突数'),
    (N'COLUMN', N'md_sync_batch', N'created_count', N'新增数'),
    (N'COLUMN', N'md_sync_batch', N'updated_count', N'更新数'),
    (N'COLUMN', N'md_sync_batch', N'unchanged_count', N'未变化数'),
    (N'COLUMN', N'md_sync_batch', N'source_missing_count', N'来源缺失数'),
    (N'COLUMN', N'md_sync_batch', N'active_count', N'同步完成后有效数'),
    (N'COLUMN', N'md_sync_batch', N'started_at', N'开始时间（世界协调时）'),
    (N'COLUMN', N'md_sync_batch', N'finished_at', N'结束时间（世界协调时）'),
    (N'COLUMN', N'md_sync_batch', N'completed_at', N'同步完成时间（世界协调时）'),
    (N'COLUMN', N'md_sync_batch', N'failure_code', N'失败代码'),
    (N'COLUMN', N'md_sync_batch', N'failure_summary', N'失败摘要'),
    (N'COLUMN', N'md_sync_batch', N'created_by', N'创建人标识'),
    (N'COLUMN', N'md_sync_batch', N'created_at', N'创建时间（世界协调时）'),
    (N'COLUMN', N'md_sync_batch', N'updated_by', N'更新人标识'),
    (N'COLUMN', N'md_sync_batch', N'updated_at', N'更新时间（世界协调时）'),
    (N'COLUMN', N'md_sync_batch', N'row_version', N'并发版本'),
    (N'CONSTRAINT', N'md_sync_batch', N'pk_md_sync_batch', N'保证每个同步批次具有唯一的平台内部主键。'),
    (N'CONSTRAINT', N'md_sync_batch', N'uq_md_sync_batch_no', N'保证面向业务追踪的批次号唯一。'),
    (N'CONSTRAINT', N'md_sync_batch', N'uq_md_sync_batch_request', N'保证一次用户操作只创建一个同步批次。'),
    (N'CONSTRAINT', N'md_sync_batch', N'fk_md_sync_batch_organization', N'保证机构范围引用已存在的平台机构。'),
    (N'CONSTRAINT', N'md_sync_batch', N'fk_md_sync_batch_endpoint', N'保证批次绑定的来源服务端点存在。'),
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_scope', N'保证机构范围和平台公共范围使用正确的机构主键。'),
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_environment', N'限定平台支持的来源接口运行环境。'),
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_category', N'限定首批已确认的基础数据类别。'),
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_mode', N'限定可识别的同步模式。'),
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_mode_scope', N'保证模式与时间范围及全量依据一致。'),
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_status', N'限定已确认的同步批次状态。'),
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_active_status', N'保证活动标志与批次终态保持一致。'),
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_query_time', N'禁止查询范围结束时间早于开始时间。'),
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_processing_time', N'禁止批次处理结束时间早于开始时间。'),
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_completion_state', N'保证已完成同步运行保存当前有效数和完成时间。'),
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_counts', N'禁止保存负数批次数量事实。'),
    (N'CONSTRAINT', N'md_sync_batch', N'ck_md_sync_batch_text', N'禁止保存空白关键标识并限制请求标识最小长度。'),
    (N'CONSTRAINT', N'md_sync_batch', N'df_md_sync_batch_active', N'新批次默认占用对应业务范围的活动名额。'),
    (N'CONSTRAINT', N'md_sync_batch', N'df_md_sync_batch_mode', N'医院目录默认不使用时间范围。'),
    (N'CONSTRAINT', N'md_sync_batch', N'df_md_sync_batch_returned', N'未取得数据前实际取得数默认为零。'),
    (N'CONSTRAINT', N'md_sync_batch', N'df_md_sync_batch_duplicate', N'未发现重复前重复数默认为零。'),
    (N'CONSTRAINT', N'md_sync_batch', N'df_md_sync_batch_invalid', N'未发现无效数据前无效数默认为零。'),
    (N'CONSTRAINT', N'md_sync_batch', N'df_md_sync_batch_conflict', N'未发现冲突前冲突数默认为零。'),
    (N'CONSTRAINT', N'md_sync_batch', N'df_md_sync_batch_created_count', N'未同步前新增数默认为零。'),
    (N'CONSTRAINT', N'md_sync_batch', N'df_md_sync_batch_updated_count', N'未同步前更新数默认为零。'),
    (N'CONSTRAINT', N'md_sync_batch', N'df_md_sync_batch_unchanged', N'未完成比对前未变化数默认为零。'),
    (N'CONSTRAINT', N'md_sync_batch', N'df_md_sync_batch_source_missing', N'未完成比对前来源缺失数默认为零。'),
    (N'CONSTRAINT', N'md_sync_batch', N'df_md_sync_batch_created', N'未显式提供时以SQL Server当前UTC时间作为批次创建时间。'),
    (N'CONSTRAINT', N'md_sync_batch', N'df_md_sync_batch_updated', N'未显式提供时以SQL Server当前UTC时间作为批次更新时间。'),
    (N'INDEX', N'md_sync_batch', N'ux_md_sync_batch_active_scope', N'阻止同一来源和业务范围并发存在多个非终态批次。'),
    (N'INDEX', N'md_sync_batch', N'ix_md_sync_batch_management_query', N'支持管理端按机构、类别、状态和开始时间分页查询批次。'),

    (N'TABLE', N'md_hospital_directory', NULL, N'医院综合目录正式数据表'),
    (N'COLUMN', N'md_hospital_directory', N'id', N'正式目录主键'),
    (N'COLUMN', N'md_hospital_directory', N'organization_id', N'平台机构主键'),
    (N'COLUMN', N'md_hospital_directory', N'source_system_code', N'来源系统代码'),
    (N'COLUMN', N'md_hospital_directory', N'directory_type', N'医院综合目录类型'),
    (N'COLUMN', N'md_hospital_directory', N'source_record_code', N'来源记录编码'),
    (N'COLUMN', N'md_hospital_directory', N'source_record_name', N'来源记录名称'),
    (N'COLUMN', N'md_hospital_directory', N'mnemonic_code', N'助记码'),
    (N'COLUMN', N'md_hospital_directory', N'category_name', N'目录类别名称'),
    (N'COLUMN', N'md_hospital_directory', N'remark', N'非敏感备注'),
    (N'COLUMN', N'md_hospital_directory', N'department_code', N'关联科室编码'),
    (N'COLUMN', N'md_hospital_directory', N'department_name', N'关联科室名称'),
    (N'COLUMN', N'md_hospital_directory', N'ward_name', N'关联病区名称'),
    (N'COLUMN', N'md_hospital_directory', N'source_organization_code', N'来源机构编码'),
    (N'COLUMN', N'md_hospital_directory', N'source_enabled_flag', N'来源启用值'),
    (N'COLUMN', N'md_hospital_directory', N'is_valid', N'平台当前有效状态'),
    (N'COLUMN', N'md_hospital_directory', N'invalidated_at', N'标记无效时间（世界协调时）'),
    (N'COLUMN', N'md_hospital_directory', N'invalid_reason', N'无效原因'),
    (N'COLUMN', N'md_hospital_directory', N'first_seen_batch_id', N'首次成功批次主键'),
    (N'COLUMN', N'md_hospital_directory', N'latest_batch_id', N'当前版本来源批次主键'),
    (N'COLUMN', N'md_hospital_directory', N'first_seen_at', N'首次观察时间（世界协调时）'),
    (N'COLUMN', N'md_hospital_directory', N'last_seen_at', N'最近成功同步时间（世界协调时）'),
    (N'COLUMN', N'md_hospital_directory', N'created_at', N'创建时间（世界协调时）'),
    (N'COLUMN', N'md_hospital_directory', N'updated_at', N'更新时间（世界协调时）'),
    (N'COLUMN', N'md_hospital_directory', N'row_version', N'并发版本'),
    (N'CONSTRAINT', N'md_hospital_directory', N'pk_md_hospital_directory', N'保证每条正式目录具有唯一平台主键。'),
    (N'CONSTRAINT', N'md_hospital_directory', N'fk_md_hospital_directory_organization', N'保证正式目录归属于已存在机构。'),
    (N'CONSTRAINT', N'md_hospital_directory', N'fk_md_hospital_directory_first_batch', N'保证首次成功批次存在。'),
    (N'CONSTRAINT', N'md_hospital_directory', N'fk_md_hospital_directory_latest_batch', N'保证当前版本来源批次存在。'),
    (N'CONSTRAINT', N'md_hospital_directory', N'uq_md_hospital_directory_source', N'保证同机构、同类型来源目录编码唯一。'),
    (N'CONSTRAINT', N'md_hospital_directory', N'ck_md_hospital_directory_type', N'限定已确认的100-003四类目录。'),
    (N'CONSTRAINT', N'md_hospital_directory', N'ck_md_hospital_directory_validity', N'保证平台有效状态与无效事实一致。'),
    (N'CONSTRAINT', N'md_hospital_directory', N'ck_md_hospital_directory_text', N'禁止保存空白来源标识、编码或名称。'),
    (N'CONSTRAINT', N'md_hospital_directory', N'df_md_hospital_directory_created', N'默认使用SQL Server当前UTC时间作为创建时间。'),
    (N'CONSTRAINT', N'md_hospital_directory', N'df_md_hospital_directory_updated', N'默认使用SQL Server当前UTC时间作为更新时间。'),
    (N'CONSTRAINT', N'md_hospital_directory', N'df_md_hospital_directory_valid', N'新目录记录默认处于平台有效状态。'),
    (N'INDEX', N'md_hospital_directory', N'ix_md_hospital_directory_read', N'支持按机构、目录类型和来源编码读取当前正式目录。'),

    (N'TABLE', N'md_hospital_directory_relation', NULL, N'医院综合目录正式关系表'),
    (N'COLUMN', N'md_hospital_directory_relation', N'id', N'正式关系主键'),
    (N'COLUMN', N'md_hospital_directory_relation', N'organization_id', N'平台机构主键'),
    (N'COLUMN', N'md_hospital_directory_relation', N'source_system_code', N'来源系统代码'),
    (N'COLUMN', N'md_hospital_directory_relation', N'relation_type', N'目录关系类型'),
    (N'COLUMN', N'md_hospital_directory_relation', N'source_record_code', N'关系起点来源编码'),
    (N'COLUMN', N'md_hospital_directory_relation', N'target_record_code', N'关系终点来源编码'),
    (N'COLUMN', N'md_hospital_directory_relation', N'latest_batch_id', N'当前关系来源批次主键'),
    (N'COLUMN', N'md_hospital_directory_relation', N'created_at', N'创建时间（世界协调时）'),
    (N'CONSTRAINT', N'md_hospital_directory_relation', N'pk_md_hospital_directory_relation', N'保证每条正式关系具有唯一平台主键。'),
    (N'CONSTRAINT', N'md_hospital_directory_relation', N'fk_md_directory_relation_organization', N'保证正式关系归属于已存在机构。'),
    (N'CONSTRAINT', N'md_hospital_directory_relation', N'fk_md_directory_relation_batch', N'保证正式关系引用已存在批次。'),
    (N'CONSTRAINT', N'md_hospital_directory_relation', N'uq_md_directory_relation_source', N'保证同机构相同目录关系唯一。'),
    (N'CONSTRAINT', N'md_hospital_directory_relation', N'ck_md_directory_relation_type', N'限定已确认的目录关系类型。'),
    (N'CONSTRAINT', N'md_hospital_directory_relation', N'ck_md_directory_relation_text', N'禁止保存空白关系标识。'),
    (N'CONSTRAINT', N'md_hospital_directory_relation', N'df_md_directory_relation_created', N'默认使用SQL Server当前UTC时间作为创建时间。'),
    (N'INDEX', N'md_hospital_directory_relation', N'ix_md_directory_relation_read', N'支持由目录记录读取全部隶属关系。');

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
