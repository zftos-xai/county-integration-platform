-- 业务域: identity_access
-- 主版本: V0300
-- 脚本类型: MAIN
-- 变更说明: 建立平台本地身份、角色权限和机构数据范围基线
-- 需求依据: 开发批次任务计划DEV-004和DEV-006、平台管理底座技术设计
-- 数据边界: 仅保存平台管理身份和授权关系，不保存外部业务账号、明文密码、会话标识或医疗数据
-- 时间口径: created_at、updated_at和password_changed_at保存UTC时间
-- 回退方案: 首次部署失败时按外键逆序删除本脚本对象；共享环境执行后只允许新增归属V0300的版本化补丁

-- 表用途: 保存平台本地管理用户及强哈希密码；不保存明文密码、外部业务系统账号或浏览器会话
CREATE TABLE sys_user (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, -- 平台用户内部主键
    login_name NVARCHAR(64) NOT NULL, -- 平台本地登录名，按数据库排序规则保证唯一
    display_name NVARCHAR(100) NOT NULL, -- 管理页面和审计快照使用的显示名称
    password_hash NVARCHAR(100) NOT NULL, -- BCrypt强哈希，不保存明文或可逆密文
    primary_organization_id BIGINT NOT NULL, -- 用户主归属平台机构，不代表全部数据范围
    is_enabled BIT NOT NULL CONSTRAINT df_sys_user_enabled DEFAULT 1, -- 用户是否允许建立和继续管理会话
    must_change_password BIT NOT NULL CONSTRAINT df_sys_user_must_change_password DEFAULT 1, -- 是否必须在使用管理功能前修改初始密码
    password_changed_at DATETIME2(3) NULL, -- 最近一次完成密码修改的UTC时间；初始密码尚未修改时为空
    created_by NVARCHAR(64) NOT NULL, -- 创建人稳定主体标识快照；安全引导使用bootstrap标识
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_user_created DEFAULT SYSUTCDATETIME(), -- 用户创建UTC时间
    updated_by NVARCHAR(64) NOT NULL, -- 最后修改人稳定主体标识快照
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_user_updated DEFAULT SYSUTCDATETIME(), -- 用户最后修改UTC时间
    row_version ROWVERSION, -- SQL Server并发版本，用于后续用户管理防止覆盖
    CONSTRAINT uq_sys_user_login_name UNIQUE (login_name), -- 保证本地登录名唯一
    CONSTRAINT fk_sys_user_primary_organization FOREIGN KEY (primary_organization_id) REFERENCES org_organization(id), -- 保证主归属机构存在
    CONSTRAINT ck_sys_user_login_name_not_blank CHECK (LEN(LTRIM(RTRIM(login_name))) > 0), -- 禁止空白登录名
    CONSTRAINT ck_sys_user_display_name_not_blank CHECK (LEN(LTRIM(RTRIM(display_name))) > 0), -- 禁止空白显示名称
    CONSTRAINT ck_sys_user_password_hash_not_blank CHECK (LEN(LTRIM(RTRIM(password_hash))) > 0) -- 禁止空白密码哈希
);

-- 索引用途: 支持按主机构和启用状态进行用户管理及最后管理员检查
CREATE INDEX ix_sys_user_organization_enabled
    ON sys_user (primary_organization_id, is_enabled)
    INCLUDE (login_name, display_name, must_change_password);

-- 表用途: 保存平台管理角色；不以角色名称推导机构数据范围
CREATE TABLE sys_role (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, -- 平台角色内部主键
    role_code NVARCHAR(64) NOT NULL, -- 稳定且唯一的角色代码
    role_name NVARCHAR(100) NOT NULL, -- 管理页面展示的角色名称
    is_enabled BIT NOT NULL CONSTRAINT df_sys_role_enabled DEFAULT 1, -- 角色是否可用于授权判定
    is_system_managed BIT NOT NULL CONSTRAINT df_sys_role_system_managed DEFAULT 0, -- 是否为平台保护的系统角色
    created_by NVARCHAR(64) NOT NULL, -- 创建人稳定主体标识快照
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_role_created DEFAULT SYSUTCDATETIME(), -- 角色创建UTC时间
    updated_by NVARCHAR(64) NOT NULL, -- 最后修改人稳定主体标识快照
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_role_updated DEFAULT SYSUTCDATETIME(), -- 角色最后修改UTC时间
    row_version ROWVERSION, -- SQL Server并发版本
    CONSTRAINT uq_sys_role_code UNIQUE (role_code), -- 保证角色代码唯一
    CONSTRAINT ck_sys_role_code_not_blank CHECK (LEN(LTRIM(RTRIM(role_code))) > 0), -- 禁止空白角色代码
    CONSTRAINT ck_sys_role_name_not_blank CHECK (LEN(LTRIM(RTRIM(role_name))) > 0) -- 禁止空白角色名称
);

-- 表用途: 保存由后端代码注册的功能权限；不允许通过任意数据库字符串扩展服务端权限
CREATE TABLE sys_permission (
    permission_code NVARCHAR(64) NOT NULL PRIMARY KEY, -- 与后端PreAuthorize一致的稳定权限代码
    permission_name NVARCHAR(100) NOT NULL, -- 管理页面展示的权限名称
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_permission_created DEFAULT SYSUTCDATETIME(), -- 权限首次注册UTC时间
    CONSTRAINT ck_sys_permission_code_not_blank CHECK (LEN(LTRIM(RTRIM(permission_code))) > 0), -- 禁止空白权限代码
    CONSTRAINT ck_sys_permission_name_not_blank CHECK (LEN(LTRIM(RTRIM(permission_name))) > 0) -- 禁止空白权限名称
);

-- 表用途: 保存用户与角色的多对多授权关系；不保存机构范围
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL, -- 被授予角色的平台用户主键
    role_id BIGINT NOT NULL, -- 授予的平台角色主键
    granted_by NVARCHAR(64) NOT NULL, -- 授权人稳定主体标识快照
    granted_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_user_role_granted DEFAULT SYSUTCDATETIME(), -- 授权UTC时间
    CONSTRAINT pk_sys_user_role PRIMARY KEY (user_id, role_id), -- 防止同一用户重复获得同一角色
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id), -- 保证授权用户存在
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id) -- 保证授权角色存在
);

-- 索引用途: 支持角色变更前检查已授权用户
CREATE INDEX ix_sys_user_role_role
    ON sys_user_role (role_id, user_id);

-- 表用途: 保存角色与代码注册权限的多对多关系；不直接保存用户机构范围
CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL, -- 获得权限的平台角色主键
    permission_code NVARCHAR(64) NOT NULL, -- 被授予的代码注册权限
    granted_by NVARCHAR(64) NOT NULL, -- 授权人稳定主体标识快照
    granted_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_role_permission_granted DEFAULT SYSUTCDATETIME(), -- 授权UTC时间
    CONSTRAINT pk_sys_role_permission PRIMARY KEY (role_id, permission_code), -- 防止同一角色重复获得同一权限
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(id), -- 保证角色存在
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_code) REFERENCES sys_permission(permission_code) -- 保证权限来自注册清单
);

-- 索引用途: 支持按权限检查关联角色和最后管理员保护
CREATE INDEX ix_sys_role_permission_permission
    ON sys_role_permission (permission_code, role_id);

-- 表用途: 保存用户可访问的平台机构范围；不从角色名称或主机构字段隐式推导
CREATE TABLE sys_user_organization_scope (
    user_id BIGINT NOT NULL, -- 获得机构数据范围的平台用户主键
    organization_id BIGINT NOT NULL, -- 被授权访问的平台机构主键
    granted_by NVARCHAR(64) NOT NULL, -- 授权人稳定主体标识快照
    granted_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_user_org_scope_granted DEFAULT SYSUTCDATETIME(), -- 授权UTC时间
    CONSTRAINT pk_sys_user_org_scope PRIMARY KEY (user_id, organization_id), -- 防止同一用户重复获得同一机构范围
    CONSTRAINT fk_sys_user_org_scope_user FOREIGN KEY (user_id) REFERENCES sys_user(id), -- 保证授权用户存在
    CONSTRAINT fk_sys_user_org_scope_organization FOREIGN KEY (organization_id) REFERENCES org_organization(id) -- 保证授权机构存在
);

-- 索引用途: 支持机构停用和范围变更前检查关联用户
CREATE INDEX ix_sys_user_org_scope_organization
    ON sys_user_organization_scope (organization_id, user_id);
