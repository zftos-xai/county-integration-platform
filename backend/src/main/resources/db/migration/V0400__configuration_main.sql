-- 业务域: configuration
-- 主版本: V0400
-- 脚本类型: MAIN
-- 变更说明: 建立代码注册平台参数值、平台系统字典、外部系统和环境服务地址初始结构
-- 需求依据: 开发批次任务计划DEV-007、平台基础管理功能技术设计
-- 数据边界: 只保存获批参数键的按适用范围保存的值和平台自身字典，不保存医疗业务目录、明文凭证或外部接口报文
-- 时间规则: created_at和updated_at保存UTC时间，精度为毫秒
-- 回退方案: 首次部署失败时按sys_external_endpoint、sys_external_system、sys_dictionary_item、sys_dictionary_type、sys_parameter_value顺序删除；共享环境执行后只允许新增归属V0400的版本化补丁

-- 表中文名称: 平台参数值表
-- 表用途: 保存代码注册参数在环境及可选的适用机构下的值；不提供任意参数键创建接口，不保存外部系统明文凭证
CREATE TABLE sys_parameter_value (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_sys_parameter_value PRIMARY KEY, -- 参数值主键：平台按适用范围保存的参数值内部主键
    parameter_key NVARCHAR(64) NOT NULL, -- 参数键：必须来自后端代码注册清单的稳定键
    value_type NVARCHAR(16) NOT NULL, -- 值类型：保存写入时注册的STRING、INTEGER、DECIMAL或BOOLEAN类型
    environment_code NVARCHAR(16) NOT NULL, -- 环境代码：参数适用的DEVELOPMENT、TEST或PRODUCTION环境
    organization_id BIGINT NULL, -- 机构主键：机构级参数绑定的平台机构；全局参数为空
    parameter_value NVARCHAR(1000) NOT NULL, -- 参数值：经注册类型和范围校验后的规范化文本；禁止保存明文凭证
    is_enabled BIT NOT NULL CONSTRAINT df_sys_parameter_value_enabled DEFAULT 1, -- 启用标志：该适用范围的参数值是否参与平台配置解析
    created_by NVARCHAR(64) NOT NULL, -- 创建人标识：创建参数值的平台登录名快照
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_parameter_value_created DEFAULT SYSUTCDATETIME(), -- 创建时间：参数值创建UTC时间，精度为毫秒
    updated_by NVARCHAR(64) NOT NULL, -- 更新人标识：最后修改参数值的平台登录名快照
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_parameter_value_updated DEFAULT SYSUTCDATETIME(), -- 更新时间：参数值最后修改UTC时间，精度为毫秒
    row_version ROWVERSION, -- 并发版本：SQL Server并发版本，防止覆盖其他管理员修改
    CONSTRAINT uq_sys_parameter_value_scope UNIQUE (parameter_key, environment_code, organization_id), -- 保证同一参数键在同一环境和适用机构只有一个值
    CONSTRAINT fk_sys_parameter_value_organization FOREIGN KEY (organization_id) REFERENCES org_organization(id), -- 保证可选的适用机构引用平台机构
    CONSTRAINT ck_sys_parameter_value_key_not_blank CHECK (LEN(LTRIM(RTRIM(parameter_key))) > 0), -- 禁止空白参数键
    CONSTRAINT ck_sys_parameter_value_type CHECK (value_type IN (N'STRING', N'INTEGER', N'DECIMAL', N'BOOLEAN')), -- 限制为代码支持的基础值类型
    CONSTRAINT ck_sys_parameter_value_environment CHECK (environment_code IN (N'DEVELOPMENT', N'TEST', N'PRODUCTION')), -- 限制为批准的部署环境
    CONSTRAINT ck_sys_parameter_value_not_blank CHECK (LEN(LTRIM(RTRIM(parameter_value))) > 0) -- 禁止空白参数值
);

-- 索引用途: 支持按机构范围查询平台参数值
CREATE INDEX ix_sys_parameter_value_organization
    ON sys_parameter_value (organization_id, environment_code)
    INCLUDE (parameter_key, value_type, is_enabled, updated_at);

-- 默认参数用途: 为管理端列表和审计查询提供各环境一致、可显式调整的安全起始值
INSERT INTO sys_parameter_value (
    parameter_key, value_type, environment_code, organization_id, parameter_value,
    is_enabled, created_by, updated_by
)
VALUES
    (N'management.list.default-page-size', N'INTEGER', N'DEVELOPMENT', NULL, N'10', 1, N'SYSTEM', N'SYSTEM'),
    (N'management.list.default-page-size', N'INTEGER', N'TEST', NULL, N'10', 1, N'SYSTEM', N'SYSTEM'),
    (N'management.list.default-page-size', N'INTEGER', N'PRODUCTION', NULL, N'10', 1, N'SYSTEM', N'SYSTEM'),
    (N'management.audit.default-query-limit', N'INTEGER', N'DEVELOPMENT', NULL, N'100', 1, N'SYSTEM', N'SYSTEM'),
    (N'management.audit.default-query-limit', N'INTEGER', N'TEST', NULL, N'100', 1, N'SYSTEM', N'SYSTEM'),
    (N'management.audit.default-query-limit', N'INTEGER', N'PRODUCTION', NULL, N'100', 1, N'SYSTEM', N'SYSTEM');

-- 表中文名称: 系统字典类型表
-- 表用途: 保存平台自身展示和分类使用的字典类型；不收纳药品、诊疗、耗材、诊断等医疗业务目录
CREATE TABLE sys_dictionary_type (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_sys_dictionary_type PRIMARY KEY, -- 字典类型主键：平台字典类型内部主键
    type_code NVARCHAR(64) NOT NULL, -- 类型代码：平台系统字典稳定唯一代码
    type_name NVARCHAR(100) NOT NULL, -- 类型名称：管理页面展示的字典类型名称
    description NVARCHAR(500) NOT NULL, -- 用途说明：明确该类型服务的平台展示或分类用途
    is_enabled BIT NOT NULL CONSTRAINT df_sys_dictionary_type_enabled DEFAULT 1, -- 启用标志：字典类型是否允许新增项并参与使用
    created_by NVARCHAR(64) NOT NULL, -- 创建人标识：创建字典类型的平台登录名快照
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_dictionary_type_created DEFAULT SYSUTCDATETIME(), -- 创建时间：字典类型创建UTC时间，精度为毫秒
    updated_by NVARCHAR(64) NOT NULL, -- 更新人标识：最后修改字典类型的平台登录名快照
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_dictionary_type_updated DEFAULT SYSUTCDATETIME(), -- 更新时间：字典类型最后修改UTC时间，精度为毫秒
    row_version ROWVERSION, -- 并发版本：SQL Server并发版本，防止覆盖其他管理员修改
    CONSTRAINT uq_sys_dictionary_type_code UNIQUE (type_code), -- 保证平台系统字典类型代码唯一
    CONSTRAINT ck_sys_dictionary_type_code_not_blank CHECK (LEN(LTRIM(RTRIM(type_code))) > 0), -- 禁止空白类型代码
    CONSTRAINT ck_sys_dictionary_type_name_not_blank CHECK (LEN(LTRIM(RTRIM(type_name))) > 0), -- 禁止空白类型名称
    CONSTRAINT ck_sys_dictionary_type_description_not_blank CHECK (LEN(LTRIM(RTRIM(description))) > 0) -- 禁止缺少用途说明
);

-- 表中文名称: 系统字典项表
-- 表用途: 保存平台系统字典类型下的展示项；不保存医疗业务目录记录或外部系统主数据
CREATE TABLE sys_dictionary_item (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_sys_dictionary_item PRIMARY KEY, -- 字典项主键：平台字典项内部主键
    dictionary_type_id BIGINT NOT NULL, -- 字典类型主键：所属平台系统字典类型
    item_code NVARCHAR(64) NOT NULL, -- 字典项代码：所属类型内稳定唯一代码
    item_label NVARCHAR(200) NOT NULL, -- 字典项文本：管理页面和平台逻辑使用的展示文本
    sort_order INT NOT NULL CONSTRAINT df_sys_dictionary_item_sort DEFAULT 0, -- 展示顺序：从小到大稳定排列，范围0至999999
    is_enabled BIT NOT NULL CONSTRAINT df_sys_dictionary_item_enabled DEFAULT 1, -- 启用标志：字典项当前是否可供平台选择和解释
    created_by NVARCHAR(64) NOT NULL, -- 创建人标识：创建字典项的平台登录名快照
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_dictionary_item_created DEFAULT SYSUTCDATETIME(), -- 创建时间：字典项创建UTC时间，精度为毫秒
    updated_by NVARCHAR(64) NOT NULL, -- 更新人标识：最后修改字典项的平台登录名快照
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_dictionary_item_updated DEFAULT SYSUTCDATETIME(), -- 更新时间：字典项最后修改UTC时间，精度为毫秒
    row_version ROWVERSION, -- 并发版本：SQL Server并发版本，防止覆盖其他管理员修改
    CONSTRAINT uq_sys_dictionary_item_code UNIQUE (dictionary_type_id, item_code), -- 保证同一字典类型内项代码唯一
    CONSTRAINT fk_sys_dictionary_item_type FOREIGN KEY (dictionary_type_id) REFERENCES sys_dictionary_type(id), -- 保证字典项所属类型存在
    CONSTRAINT ck_sys_dictionary_item_code_not_blank CHECK (LEN(LTRIM(RTRIM(item_code))) > 0), -- 禁止空白字典项代码
    CONSTRAINT ck_sys_dictionary_item_label_not_blank CHECK (LEN(LTRIM(RTRIM(item_label))) > 0), -- 禁止空白展示文本
    CONSTRAINT ck_sys_dictionary_item_sort CHECK (sort_order BETWEEN 0 AND 999999) -- 限制展示顺序范围
);

-- 索引用途: 支持按字典类型、启用状态和展示顺序读取字典项
CREATE INDEX ix_sys_dictionary_item_type_enabled_sort
    ON sys_dictionary_item (dictionary_type_id, is_enabled, sort_order, item_code)
    INCLUDE (item_label, updated_at);

-- 默认字典用途: 提供平台现有页面和API已采用的交换方向、交换结果与告警级别展示用默认值
DECLARE @exchange_direction_type_id BIGINT;
DECLARE @exchange_result_type_id BIGINT;
DECLARE @alert_level_type_id BIGINT;

INSERT INTO sys_dictionary_type (type_code, type_name, description, is_enabled, created_by, updated_by)
VALUES (N'EXCHANGE_DIRECTION', N'交换方向', N'用于接口登记和交换记录的上行、下行方向展示', 1, N'SYSTEM', N'SYSTEM');
SET @exchange_direction_type_id = SCOPE_IDENTITY();

INSERT INTO sys_dictionary_item (
    dictionary_type_id, item_code, item_label, sort_order, is_enabled, created_by, updated_by
)
VALUES
    (@exchange_direction_type_id, N'UPSTREAM', N'上行', 10, 1, N'SYSTEM', N'SYSTEM'),
    (@exchange_direction_type_id, N'DOWNSTREAM', N'下行', 20, 1, N'SYSTEM', N'SYSTEM');

INSERT INTO sys_dictionary_type (type_code, type_name, description, is_enabled, created_by, updated_by)
VALUES (N'EXCHANGE_RESULT', N'交换结果', N'用于交换记录最终业务结果的统一展示', 1, N'SYSTEM', N'SYSTEM');
SET @exchange_result_type_id = SCOPE_IDENTITY();

INSERT INTO sys_dictionary_item (
    dictionary_type_id, item_code, item_label, sort_order, is_enabled, created_by, updated_by
)
VALUES
    (@exchange_result_type_id, N'SUCCESS', N'成功', 10, 1, N'SYSTEM', N'SYSTEM'),
    (@exchange_result_type_id, N'FAILURE', N'失败', 20, 1, N'SYSTEM', N'SYSTEM'),
    (@exchange_result_type_id, N'NO_RESPONSE', N'无响应', 30, 1, N'SYSTEM', N'SYSTEM'),
    (@exchange_result_type_id, N'INVALID_RESPONSE', N'响应不可解析', 40, 1, N'SYSTEM', N'SYSTEM');

INSERT INTO sys_dictionary_type (type_code, type_name, description, is_enabled, created_by, updated_by)
VALUES (N'ALERT_LEVEL', N'告警级别', N'用于告警工作队列的优先级展示和排序', 1, N'SYSTEM', N'SYSTEM');
SET @alert_level_type_id = SCOPE_IDENTITY();

INSERT INTO sys_dictionary_item (
    dictionary_type_id, item_code, item_label, sort_order, is_enabled, created_by, updated_by
)
VALUES
    (@alert_level_type_id, N'HIGH', N'高', 10, 1, N'SYSTEM', N'SYSTEM'),
    (@alert_level_type_id, N'MEDIUM', N'中', 20, 1, N'SYSTEM', N'SYSTEM'),
    (@alert_level_type_id, N'LOW', N'低', 30, 1, N'SYSTEM', N'SYSTEM');

-- 表中文名称: 外部系统表
-- 表用途: 保存经确认的外部系统身份和启停状态；不承担业务接口注册，不保存地址或凭证
CREATE TABLE sys_external_system (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_sys_external_system PRIMARY KEY, -- 外部系统主键：平台内部主键
    system_code NVARCHAR(64) NOT NULL, -- 系统代码：经确认的稳定唯一代码
    system_name NVARCHAR(100) NOT NULL, -- 系统名称：管理页面展示名称
    description NVARCHAR(500) NOT NULL, -- 用途说明：该系统与平台的连接用途
    is_enabled BIT NOT NULL CONSTRAINT df_sys_external_system_enabled DEFAULT 1, -- 启用标志：是否允许其服务地址参与解析
    created_by NVARCHAR(64) NOT NULL, -- 创建人标识：平台登录名快照
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_external_system_created DEFAULT SYSUTCDATETIME(), -- 创建时间：UTC毫秒
    updated_by NVARCHAR(64) NOT NULL, -- 修改人标识：平台登录名快照
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_external_system_updated DEFAULT SYSUTCDATETIME(), -- 修改时间：UTC毫秒
    row_version ROWVERSION, -- 并发版本：防止覆盖其他管理员修改
    CONSTRAINT uq_sys_external_system_code UNIQUE (system_code), -- 保证系统代码唯一
    CONSTRAINT ck_sys_external_system_code_not_blank CHECK (LEN(LTRIM(RTRIM(system_code))) > 0), -- 禁止空白代码
    CONSTRAINT ck_sys_external_system_name_not_blank CHECK (LEN(LTRIM(RTRIM(system_name))) > 0), -- 禁止空白名称
    CONSTRAINT ck_sys_external_system_description_not_blank CHECK (LEN(LTRIM(RTRIM(description))) > 0) -- 禁止缺少用途说明
);

-- 表中文名称: 外部系统服务地址表
-- 表用途: 保存外部系统在环境和可选的适用机构下的受控连接参数；凭证字段仅保存批准密钥来源的引用，不保存明文凭证
CREATE TABLE sys_external_endpoint (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_sys_external_endpoint PRIMARY KEY, -- 服务地址主键：平台内部主键
    external_system_id BIGINT NOT NULL, -- 外部系统主键：所属已确认系统
    environment_code NVARCHAR(16) NOT NULL, -- 环境代码：DEVELOPMENT、TEST或PRODUCTION
    organization_id BIGINT NULL, -- 机构主键：机构级服务地址；全局服务地址为空
    base_url NVARCHAR(500) NOT NULL, -- 基础地址：经服务端校验的HTTPS地址，不含账号密码和查询串
    connect_timeout_ms INT NOT NULL, -- 连接超时：毫秒，100至60000
    read_timeout_ms INT NOT NULL, -- 读取超时：毫秒，不小于连接超时且不超过300000
    credential_reference NVARCHAR(200) NOT NULL, -- 凭证引用：医院批准密钥来源的不透明引用，禁止保存密码、Token、验证码或私钥明文
    is_enabled BIT NOT NULL CONSTRAINT df_sys_external_endpoint_enabled DEFAULT 0, -- 启用标志：新服务地址默认停用，核对后显式启用
    created_by NVARCHAR(64) NOT NULL, -- 创建人标识：平台登录名快照
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_external_endpoint_created DEFAULT SYSUTCDATETIME(), -- 创建时间：UTC毫秒
    updated_by NVARCHAR(64) NOT NULL, -- 修改人标识：平台登录名快照
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_external_endpoint_updated DEFAULT SYSUTCDATETIME(), -- 修改时间：UTC毫秒
    row_version ROWVERSION, -- 并发版本：防止覆盖其他管理员修改
    CONSTRAINT uq_sys_external_endpoint_scope UNIQUE (external_system_id, environment_code, organization_id), -- 保证系统、适用环境和机构唯一
    CONSTRAINT fk_sys_external_endpoint_system FOREIGN KEY (external_system_id) REFERENCES sys_external_system(id), -- 保证服务地址所属系统存在
    CONSTRAINT fk_sys_external_endpoint_organization FOREIGN KEY (organization_id) REFERENCES org_organization(id), -- 保证适用机构存在
    CONSTRAINT ck_sys_external_endpoint_environment CHECK (environment_code IN (N'DEVELOPMENT', N'TEST', N'PRODUCTION')), -- 限制环境代码
    CONSTRAINT ck_sys_external_endpoint_url_not_blank CHECK (LEN(LTRIM(RTRIM(base_url))) > 0), -- 禁止空白地址
    CONSTRAINT ck_sys_external_endpoint_connect_timeout CHECK (connect_timeout_ms BETWEEN 100 AND 60000), -- 限制连接超时
    CONSTRAINT ck_sys_external_endpoint_read_timeout CHECK (read_timeout_ms BETWEEN connect_timeout_ms AND 300000), -- 限制读取超时
    CONSTRAINT ck_sys_external_endpoint_credential_not_blank CHECK (LEN(LTRIM(RTRIM(credential_reference))) > 0) -- 禁止空白凭证引用
);

-- 索引用途: 支持按机构范围、环境和启用状态解析外部系统服务地址
CREATE INDEX ix_sys_external_endpoint_organization_environment
    ON sys_external_endpoint (organization_id, environment_code, is_enabled)
    INCLUDE (external_system_id, base_url, connect_timeout_ms, read_timeout_ms, updated_at);

DECLARE @object_descriptions TABLE (
    object_type NVARCHAR(16) NOT NULL,
    table_name SYSNAME NOT NULL,
    object_name SYSNAME NULL,
    description NVARCHAR(1000) NOT NULL
);

INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
VALUES
    (N'TABLE', N'sys_parameter_value', NULL, N'平台参数值表'),
    (N'COLUMN', N'sys_parameter_value', N'id', N'参数值主键'),
    (N'COLUMN', N'sys_parameter_value', N'parameter_key', N'参数键'),
    (N'COLUMN', N'sys_parameter_value', N'value_type', N'值类型'),
    (N'COLUMN', N'sys_parameter_value', N'environment_code', N'环境代码'),
    (N'COLUMN', N'sys_parameter_value', N'organization_id', N'机构主键'),
    (N'COLUMN', N'sys_parameter_value', N'parameter_value', N'参数值'),
    (N'COLUMN', N'sys_parameter_value', N'is_enabled', N'启用标志'),
    (N'COLUMN', N'sys_parameter_value', N'created_by', N'创建人标识'),
    (N'COLUMN', N'sys_parameter_value', N'created_at', N'创建时间'),
    (N'COLUMN', N'sys_parameter_value', N'updated_by', N'更新人标识'),
    (N'COLUMN', N'sys_parameter_value', N'updated_at', N'更新时间'),
    (N'COLUMN', N'sys_parameter_value', N'row_version', N'并发版本'),
    (N'CONSTRAINT', N'sys_parameter_value', N'pk_sys_parameter_value', N'保证每个按适用范围保存的参数值具有唯一内部主键。'),
    (N'CONSTRAINT', N'sys_parameter_value', N'uq_sys_parameter_value_scope', N'保证同一参数键在同一环境和适用机构只有一个值。'),
    (N'CONSTRAINT', N'sys_parameter_value', N'fk_sys_parameter_value_organization', N'保证可选的适用机构引用平台机构。'),
    (N'CONSTRAINT', N'sys_parameter_value', N'ck_sys_parameter_value_key_not_blank', N'禁止保存空白参数键。'),
    (N'CONSTRAINT', N'sys_parameter_value', N'ck_sys_parameter_value_type', N'限制为代码支持的基础参数值类型。'),
    (N'CONSTRAINT', N'sys_parameter_value', N'ck_sys_parameter_value_environment', N'限制为批准的部署环境。'),
    (N'CONSTRAINT', N'sys_parameter_value', N'ck_sys_parameter_value_not_blank', N'禁止保存空白参数值。'),
    (N'CONSTRAINT', N'sys_parameter_value', N'df_sys_parameter_value_enabled', N'新按适用范围保存的参数值默认启用。'),
    (N'CONSTRAINT', N'sys_parameter_value', N'df_sys_parameter_value_created', N'默认使用SQL Server当前UTC时间作为创建时间。'),
    (N'CONSTRAINT', N'sys_parameter_value', N'df_sys_parameter_value_updated', N'默认使用SQL Server当前UTC时间作为更新时间。'),
    (N'INDEX', N'sys_parameter_value', N'ix_sys_parameter_value_organization', N'支持按机构范围和环境查询平台参数值。'),

    (N'TABLE', N'sys_dictionary_type', NULL, N'系统字典类型表'),
    (N'COLUMN', N'sys_dictionary_type', N'id', N'字典类型主键'),
    (N'COLUMN', N'sys_dictionary_type', N'type_code', N'类型代码'),
    (N'COLUMN', N'sys_dictionary_type', N'type_name', N'类型名称'),
    (N'COLUMN', N'sys_dictionary_type', N'description', N'用途说明'),
    (N'COLUMN', N'sys_dictionary_type', N'is_enabled', N'启用标志'),
    (N'COLUMN', N'sys_dictionary_type', N'created_by', N'创建人标识'),
    (N'COLUMN', N'sys_dictionary_type', N'created_at', N'创建时间'),
    (N'COLUMN', N'sys_dictionary_type', N'updated_by', N'更新人标识'),
    (N'COLUMN', N'sys_dictionary_type', N'updated_at', N'更新时间'),
    (N'COLUMN', N'sys_dictionary_type', N'row_version', N'并发版本'),
    (N'CONSTRAINT', N'sys_dictionary_type', N'pk_sys_dictionary_type', N'保证每个系统字典类型具有唯一内部主键。'),
    (N'CONSTRAINT', N'sys_dictionary_type', N'uq_sys_dictionary_type_code', N'保证平台系统字典类型代码唯一。'),
    (N'CONSTRAINT', N'sys_dictionary_type', N'ck_sys_dictionary_type_code_not_blank', N'禁止保存空白字典类型代码。'),
    (N'CONSTRAINT', N'sys_dictionary_type', N'ck_sys_dictionary_type_name_not_blank', N'禁止保存空白字典类型名称。'),
    (N'CONSTRAINT', N'sys_dictionary_type', N'ck_sys_dictionary_type_description_not_blank', N'禁止缺少字典类型用途说明。'),
    (N'CONSTRAINT', N'sys_dictionary_type', N'df_sys_dictionary_type_enabled', N'新字典类型默认启用。'),
    (N'CONSTRAINT', N'sys_dictionary_type', N'df_sys_dictionary_type_created', N'默认使用SQL Server当前UTC时间作为创建时间。'),
    (N'CONSTRAINT', N'sys_dictionary_type', N'df_sys_dictionary_type_updated', N'默认使用SQL Server当前UTC时间作为更新时间。'),

    (N'TABLE', N'sys_dictionary_item', NULL, N'系统字典项表'),
    (N'COLUMN', N'sys_dictionary_item', N'id', N'字典项主键'),
    (N'COLUMN', N'sys_dictionary_item', N'dictionary_type_id', N'字典类型主键'),
    (N'COLUMN', N'sys_dictionary_item', N'item_code', N'字典项代码'),
    (N'COLUMN', N'sys_dictionary_item', N'item_label', N'字典项文本'),
    (N'COLUMN', N'sys_dictionary_item', N'sort_order', N'展示顺序'),
    (N'COLUMN', N'sys_dictionary_item', N'is_enabled', N'启用标志'),
    (N'COLUMN', N'sys_dictionary_item', N'created_by', N'创建人标识'),
    (N'COLUMN', N'sys_dictionary_item', N'created_at', N'创建时间'),
    (N'COLUMN', N'sys_dictionary_item', N'updated_by', N'更新人标识'),
    (N'COLUMN', N'sys_dictionary_item', N'updated_at', N'更新时间'),
    (N'COLUMN', N'sys_dictionary_item', N'row_version', N'并发版本'),
    (N'CONSTRAINT', N'sys_dictionary_item', N'pk_sys_dictionary_item', N'保证每个系统字典项具有唯一内部主键。'),
    (N'CONSTRAINT', N'sys_dictionary_item', N'uq_sys_dictionary_item_code', N'保证同一字典类型内项代码唯一。'),
    (N'CONSTRAINT', N'sys_dictionary_item', N'fk_sys_dictionary_item_type', N'保证字典项所属类型存在。'),
    (N'CONSTRAINT', N'sys_dictionary_item', N'ck_sys_dictionary_item_code_not_blank', N'禁止保存空白字典项代码。'),
    (N'CONSTRAINT', N'sys_dictionary_item', N'ck_sys_dictionary_item_label_not_blank', N'禁止保存空白字典项展示文本。'),
    (N'CONSTRAINT', N'sys_dictionary_item', N'ck_sys_dictionary_item_sort', N'限制展示顺序范围为0至999999。'),
    (N'CONSTRAINT', N'sys_dictionary_item', N'df_sys_dictionary_item_sort', N'新字典项默认展示顺序为0。'),
    (N'CONSTRAINT', N'sys_dictionary_item', N'df_sys_dictionary_item_enabled', N'新字典项默认启用。'),
    (N'CONSTRAINT', N'sys_dictionary_item', N'df_sys_dictionary_item_created', N'默认使用SQL Server当前UTC时间作为创建时间。'),
    (N'CONSTRAINT', N'sys_dictionary_item', N'df_sys_dictionary_item_updated', N'默认使用SQL Server当前UTC时间作为更新时间。'),
    (N'INDEX', N'sys_dictionary_item', N'ix_sys_dictionary_item_type_enabled_sort', N'支持按字典类型、启用状态和展示顺序读取字典项。'),

    (N'TABLE', N'sys_external_system', NULL, N'外部系统表'),
    (N'COLUMN', N'sys_external_system', N'id', N'外部系统主键'),
    (N'COLUMN', N'sys_external_system', N'system_code', N'系统代码'),
    (N'COLUMN', N'sys_external_system', N'system_name', N'系统名称'),
    (N'COLUMN', N'sys_external_system', N'description', N'用途说明'),
    (N'COLUMN', N'sys_external_system', N'is_enabled', N'启用标志'),
    (N'COLUMN', N'sys_external_system', N'created_by', N'创建人标识'),
    (N'COLUMN', N'sys_external_system', N'created_at', N'创建时间'),
    (N'COLUMN', N'sys_external_system', N'updated_by', N'修改人标识'),
    (N'COLUMN', N'sys_external_system', N'updated_at', N'修改时间'),
    (N'COLUMN', N'sys_external_system', N'row_version', N'并发版本'),
    (N'CONSTRAINT', N'sys_external_system', N'pk_sys_external_system', N'保证每个外部系统具有唯一内部主键。'),
    (N'CONSTRAINT', N'sys_external_system', N'uq_sys_external_system_code', N'保证外部系统代码唯一。'),
    (N'CONSTRAINT', N'sys_external_system', N'ck_sys_external_system_code_not_blank', N'禁止保存空白外部系统代码。'),
    (N'CONSTRAINT', N'sys_external_system', N'ck_sys_external_system_name_not_blank', N'禁止保存空白外部系统名称。'),
    (N'CONSTRAINT', N'sys_external_system', N'ck_sys_external_system_description_not_blank', N'禁止缺少外部系统用途说明。'),
    (N'CONSTRAINT', N'sys_external_system', N'df_sys_external_system_enabled', N'新外部系统默认启用。'),
    (N'CONSTRAINT', N'sys_external_system', N'df_sys_external_system_created', N'默认使用SQL Server当前UTC时间作为创建时间。'),
    (N'CONSTRAINT', N'sys_external_system', N'df_sys_external_system_updated', N'默认使用SQL Server当前UTC时间作为修改时间。'),

    (N'TABLE', N'sys_external_endpoint', NULL, N'外部系统服务地址表'),
    (N'COLUMN', N'sys_external_endpoint', N'id', N'服务地址主键'),
    (N'COLUMN', N'sys_external_endpoint', N'external_system_id', N'外部系统主键'),
    (N'COLUMN', N'sys_external_endpoint', N'environment_code', N'环境代码'),
    (N'COLUMN', N'sys_external_endpoint', N'organization_id', N'机构主键'),
    (N'COLUMN', N'sys_external_endpoint', N'base_url', N'基础地址'),
    (N'COLUMN', N'sys_external_endpoint', N'connect_timeout_ms', N'连接超时（毫秒）'),
    (N'COLUMN', N'sys_external_endpoint', N'read_timeout_ms', N'读取超时（毫秒）'),
    (N'COLUMN', N'sys_external_endpoint', N'credential_reference', N'凭证引用（禁止保存明文凭证）'),
    (N'COLUMN', N'sys_external_endpoint', N'is_enabled', N'启用标志'),
    (N'COLUMN', N'sys_external_endpoint', N'created_by', N'创建人标识'),
    (N'COLUMN', N'sys_external_endpoint', N'created_at', N'创建时间'),
    (N'COLUMN', N'sys_external_endpoint', N'updated_by', N'修改人标识'),
    (N'COLUMN', N'sys_external_endpoint', N'updated_at', N'修改时间'),
    (N'COLUMN', N'sys_external_endpoint', N'row_version', N'并发版本'),
    (N'CONSTRAINT', N'sys_external_endpoint', N'pk_sys_external_endpoint', N'保证每个外部系统服务地址具有唯一内部主键。'),
    (N'CONSTRAINT', N'sys_external_endpoint', N'uq_sys_external_endpoint_scope', N'保证系统、适用环境和机构唯一。'),
    (N'CONSTRAINT', N'sys_external_endpoint', N'fk_sys_external_endpoint_system', N'保证服务地址所属外部系统存在。'),
    (N'CONSTRAINT', N'sys_external_endpoint', N'fk_sys_external_endpoint_organization', N'保证可选的适用机构存在。'),
    (N'CONSTRAINT', N'sys_external_endpoint', N'ck_sys_external_endpoint_environment', N'限制为平台支持的部署环境。'),
    (N'CONSTRAINT', N'sys_external_endpoint', N'ck_sys_external_endpoint_url_not_blank', N'禁止保存空白服务地址地址。'),
    (N'CONSTRAINT', N'sys_external_endpoint', N'ck_sys_external_endpoint_connect_timeout', N'限制连接超时为100至60000毫秒。'),
    (N'CONSTRAINT', N'sys_external_endpoint', N'ck_sys_external_endpoint_read_timeout', N'限制读取超时不小于连接超时且不超过300000毫秒。'),
    (N'CONSTRAINT', N'sys_external_endpoint', N'ck_sys_external_endpoint_credential_not_blank', N'禁止保存空白凭证引用。'),
    (N'CONSTRAINT', N'sys_external_endpoint', N'df_sys_external_endpoint_enabled', N'新外部系统服务地址默认停用。'),
    (N'CONSTRAINT', N'sys_external_endpoint', N'df_sys_external_endpoint_created', N'默认使用SQL Server当前UTC时间作为创建时间。'),
    (N'CONSTRAINT', N'sys_external_endpoint', N'df_sys_external_endpoint_updated', N'默认使用SQL Server当前UTC时间作为修改时间。'),
    (N'INDEX', N'sys_external_endpoint', N'ix_sys_external_endpoint_organization_environment', N'支持按机构范围、环境和启用状态解析服务地址。');

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
