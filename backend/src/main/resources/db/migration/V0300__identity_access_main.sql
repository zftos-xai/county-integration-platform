-- 业务域: identity_access
-- 主版本: V0300
-- 脚本类型: MAIN
-- 变更说明: 建立平台本地身份、角色权限和机构数据已确认的范围
-- 需求依据: 开发批次任务计划DEV-004和DEV-006、平台基础管理功能技术设计
-- 数据边界: 仅保存平台管理身份和授权关系，不保存外部业务账号、明文密码、会话标识或医疗数据
-- 时间规则: created_at、updated_at和password_changed_at保存UTC时间
-- 回退方案: 首次部署失败时按外键逆序删除本脚本对象；共享环境执行后只允许新增归属V0300的版本化补丁

-- 表中文名称: 平台用户表
-- 表用途: 保存平台本地管理用户及强哈希密码；不保存明文密码、外部业务系统账号或浏览器会话
CREATE TABLE sys_user (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_sys_user PRIMARY KEY, -- 用户主键：平台用户内部主键
    login_name NVARCHAR(64) NOT NULL, -- 登录名：平台本地登录名，按数据库排序规则保证唯一
    display_name NVARCHAR(100) NOT NULL, -- 显示名称：管理页面和审计快照使用的显示名称
    password_hash NVARCHAR(100) NOT NULL, -- 密码哈希：BCrypt强哈希，不保存明文或可逆密文
    primary_organization_id BIGINT NOT NULL, -- 主归属机构主键：用户主归属平台机构，不代表全部数据范围
    is_enabled BIT NOT NULL CONSTRAINT df_sys_user_enabled DEFAULT 1, -- 启用标志：用户是否允许建立和继续管理会话
    must_change_password BIT NOT NULL CONSTRAINT df_sys_user_must_change_password DEFAULT 1, -- 强制修改密码标志：是否必须在使用管理功能前修改初始密码
    password_changed_at DATETIME2(3) NULL, -- 密码修改时间：最近一次完成密码修改的UTC时间；初始密码尚未修改时为空
    created_by NVARCHAR(64) NOT NULL, -- 创建人标识：创建人稳定用户标识快照；安全引导使用bootstrap标识
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_user_created DEFAULT SYSUTCDATETIME(), -- 创建时间：用户创建UTC时间
    updated_by NVARCHAR(64) NOT NULL, -- 更新人标识：最后修改人稳定用户标识快照
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_user_updated DEFAULT SYSUTCDATETIME(), -- 更新时间：用户最后修改UTC时间
    row_version ROWVERSION, -- 并发版本：SQL Server并发版本，用于后续用户管理防止覆盖
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

-- 表中文名称: 角色表
-- 表用途: 保存平台管理角色；不以角色名称推导机构数据范围
CREATE TABLE sys_role (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_sys_role PRIMARY KEY, -- 角色主键：平台角色内部主键
    role_code NVARCHAR(64) NOT NULL, -- 角色代码：稳定且唯一的角色代码
    role_name NVARCHAR(100) NOT NULL, -- 角色名称：管理页面展示的角色名称
    is_enabled BIT NOT NULL CONSTRAINT df_sys_role_enabled DEFAULT 1, -- 启用标志：角色是否可用于授权判定
    is_system_managed BIT NOT NULL CONSTRAINT df_sys_role_system_managed DEFAULT 0, -- 系统管理标志：是否为平台保护的系统角色
    created_by NVARCHAR(64) NOT NULL, -- 创建人标识：创建人稳定用户标识快照
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_role_created DEFAULT SYSUTCDATETIME(), -- 创建时间：角色创建UTC时间
    updated_by NVARCHAR(64) NOT NULL, -- 更新人标识：最后修改人稳定用户标识快照
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_role_updated DEFAULT SYSUTCDATETIME(), -- 更新时间：角色最后修改UTC时间
    row_version ROWVERSION, -- 并发版本：SQL Server并发版本
    CONSTRAINT uq_sys_role_code UNIQUE (role_code), -- 保证角色代码唯一
    CONSTRAINT ck_sys_role_code_not_blank CHECK (LEN(LTRIM(RTRIM(role_code))) > 0), -- 禁止空白角色代码
    CONSTRAINT ck_sys_role_name_not_blank CHECK (LEN(LTRIM(RTRIM(role_name))) > 0) -- 禁止空白角色名称
);

-- 表中文名称: 功能权限表
-- 表用途: 保存由后端代码注册的功能权限；不允许通过任意数据库字符串扩展服务端权限
CREATE TABLE sys_permission (
    permission_code NVARCHAR(64) NOT NULL CONSTRAINT pk_sys_permission PRIMARY KEY, -- 权限代码：与后端PreAuthorize一致的稳定权限代码
    permission_name NVARCHAR(100) NOT NULL, -- 权限名称：管理页面展示的权限名称
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_permission_created DEFAULT SYSUTCDATETIME(), -- 创建时间：权限首次注册UTC时间
    CONSTRAINT ck_sys_permission_code_not_blank CHECK (LEN(LTRIM(RTRIM(permission_code))) > 0), -- 禁止空白权限代码
    CONSTRAINT ck_sys_permission_name_not_blank CHECK (LEN(LTRIM(RTRIM(permission_name))) > 0) -- 禁止空白权限名称
);

-- 表中文名称: 用户角色关系表
-- 表用途: 保存用户与角色的多对多授权关系；不保存机构范围
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL, -- 用户主键：被授予角色的平台用户主键
    role_id BIGINT NOT NULL, -- 角色主键：授予的平台角色主键
    granted_by NVARCHAR(64) NOT NULL, -- 授权人标识：授权人稳定用户标识快照
    granted_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_user_role_granted DEFAULT SYSUTCDATETIME(), -- 授权时间：授权UTC时间
    CONSTRAINT pk_sys_user_role PRIMARY KEY (user_id, role_id), -- 防止同一用户重复获得同一角色
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id), -- 保证授权用户存在
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id) -- 保证授权角色存在
);

-- 索引用途: 支持角色变更前检查已授权用户
CREATE INDEX ix_sys_user_role_role
    ON sys_user_role (role_id, user_id);

-- 表中文名称: 角色权限关系表
-- 表用途: 保存角色与代码注册权限的多对多关系；不直接保存用户机构范围
CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL, -- 角色主键：获得权限的平台角色主键
    permission_code NVARCHAR(64) NOT NULL, -- 权限代码：被授予的代码注册权限
    granted_by NVARCHAR(64) NOT NULL, -- 授权人标识：授权人稳定用户标识快照
    granted_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_role_permission_granted DEFAULT SYSUTCDATETIME(), -- 授权时间：授权UTC时间
    CONSTRAINT pk_sys_role_permission PRIMARY KEY (role_id, permission_code), -- 防止同一角色重复获得同一权限
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(id), -- 保证角色存在
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_code) REFERENCES sys_permission(permission_code) -- 保证权限来自注册清单
);

-- 索引用途: 支持按权限检查关联角色和最后管理员保护
CREATE INDEX ix_sys_role_permission_permission
    ON sys_role_permission (permission_code, role_id);

-- 表中文名称: 用户机构数据范围表
-- 表用途: 保存用户可访问的平台机构范围；不从角色名称或主机构字段隐式推导
CREATE TABLE sys_user_organization_scope (
    user_id BIGINT NOT NULL, -- 用户主键：获得机构数据范围的平台用户主键
    organization_id BIGINT NOT NULL, -- 机构主键：被授权访问的平台机构主键
    granted_by NVARCHAR(64) NOT NULL, -- 授权人标识：授权人稳定用户标识快照
    granted_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_user_org_scope_granted DEFAULT SYSUTCDATETIME(), -- 授权时间：授权UTC时间
    CONSTRAINT pk_sys_user_org_scope PRIMARY KEY (user_id, organization_id), -- 防止同一用户重复获得同一机构范围
    CONSTRAINT fk_sys_user_org_scope_user FOREIGN KEY (user_id) REFERENCES sys_user(id), -- 保证授权用户存在
    CONSTRAINT fk_sys_user_org_scope_organization FOREIGN KEY (organization_id) REFERENCES org_organization(id) -- 保证授权机构存在
);

-- 索引用途: 支持机构停用和范围变更前检查关联用户
CREATE INDEX ix_sys_user_org_scope_organization
    ON sys_user_organization_scope (organization_id, user_id);

DECLARE @object_descriptions TABLE (
    object_type NVARCHAR(16) NOT NULL,
    table_name SYSNAME NOT NULL,
    object_name SYSNAME NULL,
    description NVARCHAR(1000) NOT NULL
);

INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
VALUES
    (N'TABLE', N'sys_user', NULL, N'平台用户表'),
    (N'COLUMN', N'sys_user', N'id', N'用户主键'),
    (N'COLUMN', N'sys_user', N'login_name', N'登录名'),
    (N'COLUMN', N'sys_user', N'display_name', N'显示名称'),
    (N'COLUMN', N'sys_user', N'password_hash', N'密码哈希'),
    (N'COLUMN', N'sys_user', N'primary_organization_id', N'主归属机构主键'),
    (N'COLUMN', N'sys_user', N'is_enabled', N'启用标志'),
    (N'COLUMN', N'sys_user', N'must_change_password', N'强制修改密码标志'),
    (N'COLUMN', N'sys_user', N'password_changed_at', N'密码修改时间'),
    (N'COLUMN', N'sys_user', N'created_by', N'创建人标识'),
    (N'COLUMN', N'sys_user', N'created_at', N'创建时间'),
    (N'COLUMN', N'sys_user', N'updated_by', N'更新人标识'),
    (N'COLUMN', N'sys_user', N'updated_at', N'更新时间'),
    (N'COLUMN', N'sys_user', N'row_version', N'并发版本'),
    (N'CONSTRAINT', N'sys_user', N'pk_sys_user', N'保证每个平台用户具有唯一的内部主键。'),
    (N'CONSTRAINT', N'sys_user', N'uq_sys_user_login_name', N'保证平台本地登录名唯一。'),
    (N'CONSTRAINT', N'sys_user', N'fk_sys_user_primary_organization', N'保证用户主归属机构存在。'),
    (N'CONSTRAINT', N'sys_user', N'ck_sys_user_login_name_not_blank', N'禁止保存空白登录名。'),
    (N'CONSTRAINT', N'sys_user', N'ck_sys_user_display_name_not_blank', N'禁止保存空白显示名称。'),
    (N'CONSTRAINT', N'sys_user', N'ck_sys_user_password_hash_not_blank', N'禁止保存空白密码哈希。'),
    (N'CONSTRAINT', N'sys_user', N'df_sys_user_enabled', N'未显式提供时，将新平台用户默认设为启用。'),
    (N'CONSTRAINT', N'sys_user', N'df_sys_user_must_change_password', N'未显式提供时，要求新平台用户首次使用管理功能前修改初始密码。'),
    (N'CONSTRAINT', N'sys_user', N'df_sys_user_created', N'未显式提供时，以SQL Server当前UTC时间作为用户创建时间。'),
    (N'CONSTRAINT', N'sys_user', N'df_sys_user_updated', N'未显式提供时，以SQL Server当前UTC时间作为用户最后修改时间。'),
    (N'INDEX', N'sys_user', N'ix_sys_user_organization_enabled', N'支持按主机构和启用状态进行用户管理及最后管理员检查。'),

    (N'TABLE', N'sys_role', NULL, N'角色表'),
    (N'COLUMN', N'sys_role', N'id', N'角色主键'),
    (N'COLUMN', N'sys_role', N'role_code', N'角色代码'),
    (N'COLUMN', N'sys_role', N'role_name', N'角色名称'),
    (N'COLUMN', N'sys_role', N'is_enabled', N'启用标志'),
    (N'COLUMN', N'sys_role', N'is_system_managed', N'系统管理标志'),
    (N'COLUMN', N'sys_role', N'created_by', N'创建人标识'),
    (N'COLUMN', N'sys_role', N'created_at', N'创建时间'),
    (N'COLUMN', N'sys_role', N'updated_by', N'更新人标识'),
    (N'COLUMN', N'sys_role', N'updated_at', N'更新时间'),
    (N'COLUMN', N'sys_role', N'row_version', N'并发版本'),
    (N'CONSTRAINT', N'sys_role', N'pk_sys_role', N'保证每个平台角色具有唯一的内部主键。'),
    (N'CONSTRAINT', N'sys_role', N'uq_sys_role_code', N'保证平台角色代码唯一。'),
    (N'CONSTRAINT', N'sys_role', N'ck_sys_role_code_not_blank', N'禁止保存空白角色代码。'),
    (N'CONSTRAINT', N'sys_role', N'ck_sys_role_name_not_blank', N'禁止保存空白角色名称。'),
    (N'CONSTRAINT', N'sys_role', N'df_sys_role_enabled', N'未显式提供时，将新角色默认设为启用。'),
    (N'CONSTRAINT', N'sys_role', N'df_sys_role_system_managed', N'未显式提供时，将新角色默认设为非系统保护角色。'),
    (N'CONSTRAINT', N'sys_role', N'df_sys_role_created', N'未显式提供时，以SQL Server当前UTC时间作为角色创建时间。'),
    (N'CONSTRAINT', N'sys_role', N'df_sys_role_updated', N'未显式提供时，以SQL Server当前UTC时间作为角色最后修改时间。'),

    (N'TABLE', N'sys_permission', NULL, N'功能权限表'),
    (N'COLUMN', N'sys_permission', N'permission_code', N'权限代码'),
    (N'COLUMN', N'sys_permission', N'permission_name', N'权限名称'),
    (N'COLUMN', N'sys_permission', N'created_at', N'创建时间'),
    (N'CONSTRAINT', N'sys_permission', N'pk_sys_permission', N'保证每个代码注册权限具有唯一的权限代码。'),
    (N'CONSTRAINT', N'sys_permission', N'ck_sys_permission_code_not_blank', N'禁止保存空白权限代码。'),
    (N'CONSTRAINT', N'sys_permission', N'ck_sys_permission_name_not_blank', N'禁止保存空白权限名称。'),
    (N'CONSTRAINT', N'sys_permission', N'df_sys_permission_created', N'未显式提供时，以SQL Server当前UTC时间作为权限首次注册时间。'),

    (N'TABLE', N'sys_user_role', NULL, N'用户角色关系表'),
    (N'COLUMN', N'sys_user_role', N'user_id', N'用户主键'),
    (N'COLUMN', N'sys_user_role', N'role_id', N'角色主键'),
    (N'COLUMN', N'sys_user_role', N'granted_by', N'授权人标识'),
    (N'COLUMN', N'sys_user_role', N'granted_at', N'授权时间'),
    (N'CONSTRAINT', N'sys_user_role', N'pk_sys_user_role', N'防止同一用户重复获得同一角色。'),
    (N'CONSTRAINT', N'sys_user_role', N'fk_sys_user_role_user', N'保证被授权的平台用户存在。'),
    (N'CONSTRAINT', N'sys_user_role', N'fk_sys_user_role_role', N'保证授予的平台角色存在。'),
    (N'CONSTRAINT', N'sys_user_role', N'df_sys_user_role_granted', N'未显式提供时，以SQL Server当前UTC时间作为用户角色授权时间。'),
    (N'INDEX', N'sys_user_role', N'ix_sys_user_role_role', N'支持角色变更前检查已授权用户。'),

    (N'TABLE', N'sys_role_permission', NULL, N'角色权限关系表'),
    (N'COLUMN', N'sys_role_permission', N'role_id', N'角色主键'),
    (N'COLUMN', N'sys_role_permission', N'permission_code', N'权限代码'),
    (N'COLUMN', N'sys_role_permission', N'granted_by', N'授权人标识'),
    (N'COLUMN', N'sys_role_permission', N'granted_at', N'授权时间'),
    (N'CONSTRAINT', N'sys_role_permission', N'pk_sys_role_permission', N'防止同一角色重复获得同一功能权限。'),
    (N'CONSTRAINT', N'sys_role_permission', N'fk_sys_role_permission_role', N'保证被授权的平台角色存在。'),
    (N'CONSTRAINT', N'sys_role_permission', N'fk_sys_role_permission_permission', N'保证授予的权限来自后端代码注册清单。'),
    (N'CONSTRAINT', N'sys_role_permission', N'df_sys_role_permission_granted', N'未显式提供时，以SQL Server当前UTC时间作为角色权限授权时间。'),
    (N'INDEX', N'sys_role_permission', N'ix_sys_role_permission_permission', N'支持按权限检查关联角色和最后管理员保护。'),

    (N'TABLE', N'sys_user_organization_scope', NULL, N'用户机构数据范围表'),
    (N'COLUMN', N'sys_user_organization_scope', N'user_id', N'用户主键'),
    (N'COLUMN', N'sys_user_organization_scope', N'organization_id', N'机构主键'),
    (N'COLUMN', N'sys_user_organization_scope', N'granted_by', N'授权人标识'),
    (N'COLUMN', N'sys_user_organization_scope', N'granted_at', N'授权时间'),
    (N'CONSTRAINT', N'sys_user_organization_scope', N'pk_sys_user_org_scope', N'防止同一用户重复获得同一机构数据范围。'),
    (N'CONSTRAINT', N'sys_user_organization_scope', N'fk_sys_user_org_scope_user', N'保证获得机构范围的平台用户存在。'),
    (N'CONSTRAINT', N'sys_user_organization_scope', N'fk_sys_user_org_scope_organization', N'保证授权访问的平台机构存在。'),
    (N'CONSTRAINT', N'sys_user_organization_scope', N'df_sys_user_org_scope_granted', N'未显式提供时，以SQL Server当前UTC时间作为机构数据范围授权时间。'),
    (N'INDEX', N'sys_user_organization_scope', N'ix_sys_user_org_scope_organization', N'支持机构停用和范围变更前检查关联用户。');

DECLARE @object_type NVARCHAR(16);
DECLARE @table_name SYSNAME;
DECLARE @object_name SYSNAME;
DECLARE @resolved_object_name SYSNAME;
DECLARE @description NVARCHAR(1000);

DECLARE description_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT object_type, table_name, object_name, description
    FROM @object_descriptions;

OPEN description_cursor;
FETCH NEXT FROM description_cursor INTO @object_type, @table_name, @object_name, @description;

WHILE @@FETCH_STATUS = 0
BEGIN
    SET @resolved_object_name = @object_name;

    IF @object_type = N'TABLE'
    BEGIN
        IF EXISTS (
            SELECT 1
            FROM sys.fn_listextendedproperty(N'MS_Description', N'SCHEMA', N'dbo', N'TABLE', @table_name, NULL, NULL)
        )
            EXEC sys.sp_updateextendedproperty @name=N'MS_Description', @value=@description,
                @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name;
        ELSE
            EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=@description,
                @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name;
    END
    ELSE
    BEGIN
        IF @resolved_object_name IS NULL
            RAISERROR(N'无法解析对象 %s.%s 的名称。', 16, 1, @table_name, @object_name);

        IF EXISTS (
            SELECT 1
            FROM sys.fn_listextendedproperty(N'MS_Description', N'SCHEMA', N'dbo', N'TABLE', @table_name, @object_type, @resolved_object_name)
        )
            EXEC sys.sp_updateextendedproperty @name=N'MS_Description', @value=@description,
                @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name,
                @level2type=@object_type, @level2name=@resolved_object_name;
        ELSE
            EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=@description,
                @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name,
                @level2type=@object_type, @level2name=@resolved_object_name;
    END;

    FETCH NEXT FROM description_cursor INTO @object_type, @table_name, @object_name, @description;
END;

CLOSE description_cursor;
DEALLOCATE description_cursor;
