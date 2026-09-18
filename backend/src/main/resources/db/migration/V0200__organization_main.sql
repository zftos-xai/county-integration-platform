-- 业务域: organization
-- 主版本: V0200
-- 脚本类型: MAIN
-- 变更说明: 建立平台机构档案、层级、启停和并发控制初始结构
-- 需求依据: 项目开发PRD第4章、开发批次任务计划DEV-005
-- 数据边界: 仅保存平台管理机构，不保存100-008上游机构原始数据或医疗业务正文
-- 时间规则: valid_from、valid_to、created_at和updated_at保存UTC时间
-- 回退方案: 首次部署失败时删除本脚本建立的索引、外键、约束和表；共享环境执行前由DBA复核

-- 表中文名称: 机构表
-- 表用途: 保存平台权限、用户归属和配置适用范围使用的受控机构档案；不承载上游机构同步原始记录
CREATE TABLE org_organization (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_org_organization PRIMARY KEY, -- 机构主键：平台内部机构主键
    organization_code NVARCHAR(64) NOT NULL, -- 机构代码：平台统一、稳定且对外使用的机构编码
    organization_name NVARCHAR(200) NOT NULL, -- 机构名称：管理界面和审计展示使用的机构名称
    organization_type NVARCHAR(32) NOT NULL, -- 机构类型：经项目确认的机构类型代码；未在本脚本预置虚构枚举
    parent_id BIGINT NULL, -- 上级机构主键：可选父机构主键；空值表示顶级机构
    is_enabled BIT NOT NULL CONSTRAINT df_org_organization_enabled DEFAULT 1, -- 启用标志：机构是否可用；停用不删除历史档案
    valid_from DATETIME2(3) NULL, -- 有效开始时间：可选业务有效起始UTC时间；空值表示未限定起始时间
    valid_to DATETIME2(3) NULL, -- 有效结束时间：可选业务有效结束UTC时间；空值表示未限定结束时间
    created_by NVARCHAR(64) NOT NULL, -- 创建人标识：创建人稳定用户标识快照；安全引导期不强制外键
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_org_organization_created DEFAULT SYSUTCDATETIME(), -- 创建时间：机构档案创建UTC时间
    updated_by NVARCHAR(64) NOT NULL, -- 更新人标识：最后修改人稳定用户标识快照
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_org_organization_updated DEFAULT SYSUTCDATETIME(), -- 更新时间：机构档案最后修改UTC时间
    row_version ROWVERSION, -- 并发版本：SQL Server并发版本，由客户端回传以防止无感知覆盖
    CONSTRAINT uq_org_organization_code UNIQUE (organization_code), -- 保证平台机构编码唯一
    CONSTRAINT fk_org_organization_parent FOREIGN KEY (parent_id) REFERENCES org_organization(id), -- 保证父机构存在；循环由应用层拒绝
    CONSTRAINT ck_org_organization_not_self CHECK (parent_id IS NULL OR parent_id <> id), -- 禁止机构直接引用自身为父机构
    CONSTRAINT ck_org_organization_code_not_blank CHECK (LEN(LTRIM(RTRIM(organization_code))) > 0), -- 禁止空白机构编码
    CONSTRAINT ck_org_organization_name_not_blank CHECK (LEN(LTRIM(RTRIM(organization_name))) > 0), -- 禁止空白机构名称
    CONSTRAINT ck_org_organization_type_not_blank CHECK (LEN(LTRIM(RTRIM(organization_type))) > 0), -- 禁止空白机构类型
    CONSTRAINT ck_org_organization_valid_time CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from) -- 禁止有效结束早于起始
);

-- 索引用途: 支持机构树按父机构和启用状态查询直接子机构
CREATE INDEX ix_org_organization_parent_enabled
    ON org_organization (parent_id, is_enabled)
    INCLUDE (organization_code, organization_name, organization_type, valid_from, valid_to);

-- 索引用途: 支持管理端按机构名称和主键稳定排序的有界列表查询
CREATE INDEX ix_org_organization_name
    ON org_organization (organization_name, id)
    INCLUDE (organization_code, organization_type, parent_id, is_enabled);

DECLARE @object_descriptions TABLE (
    object_type NVARCHAR(16) NOT NULL,
    table_name SYSNAME NOT NULL,
    object_name SYSNAME NULL,
    description NVARCHAR(1000) NOT NULL
);

INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
VALUES
    (N'TABLE', N'org_organization', NULL, N'机构表'),
    (N'COLUMN', N'org_organization', N'id', N'机构主键'),
    (N'COLUMN', N'org_organization', N'organization_code', N'机构代码'),
    (N'COLUMN', N'org_organization', N'organization_name', N'机构名称'),
    (N'COLUMN', N'org_organization', N'organization_type', N'机构类型'),
    (N'COLUMN', N'org_organization', N'parent_id', N'上级机构主键'),
    (N'COLUMN', N'org_organization', N'is_enabled', N'启用标志'),
    (N'COLUMN', N'org_organization', N'valid_from', N'有效开始时间'),
    (N'COLUMN', N'org_organization', N'valid_to', N'有效结束时间'),
    (N'COLUMN', N'org_organization', N'created_by', N'创建人标识'),
    (N'COLUMN', N'org_organization', N'created_at', N'创建时间'),
    (N'COLUMN', N'org_organization', N'updated_by', N'更新人标识'),
    (N'COLUMN', N'org_organization', N'updated_at', N'更新时间'),
    (N'COLUMN', N'org_organization', N'row_version', N'并发版本'),
    (N'CONSTRAINT', N'org_organization', N'pk_org_organization', N'保证每个机构档案具有唯一的平台内部主键。'),
    (N'CONSTRAINT', N'org_organization', N'uq_org_organization_code', N'保证平台机构编码唯一。'),
    (N'CONSTRAINT', N'org_organization', N'fk_org_organization_parent', N'保证父机构存在；机构层级循环由应用层拒绝。'),
    (N'CONSTRAINT', N'org_organization', N'ck_org_organization_not_self', N'禁止机构直接引用自身为父机构。'),
    (N'CONSTRAINT', N'org_organization', N'ck_org_organization_code_not_blank', N'禁止保存空白机构编码。'),
    (N'CONSTRAINT', N'org_organization', N'ck_org_organization_name_not_blank', N'禁止保存空白机构名称。'),
    (N'CONSTRAINT', N'org_organization', N'ck_org_organization_type_not_blank', N'禁止保存空白机构类型。'),
    (N'CONSTRAINT', N'org_organization', N'ck_org_organization_valid_time', N'禁止有效结束时间早于有效起始时间。'),
    (N'CONSTRAINT', N'org_organization', N'df_org_organization_enabled', N'未显式提供时，将新机构默认设为启用。'),
    (N'CONSTRAINT', N'org_organization', N'df_org_organization_created', N'未显式提供时，以SQL Server当前UTC时间作为机构创建时间。'),
    (N'CONSTRAINT', N'org_organization', N'df_org_organization_updated', N'未显式提供时，以SQL Server当前UTC时间作为机构最后修改时间。'),
    (N'INDEX', N'org_organization', N'ix_org_organization_parent_enabled', N'支持机构树按父机构和启用状态查询直接子机构。'),
    (N'INDEX', N'org_organization', N'ix_org_organization_name', N'支持管理端按机构名称和主键稳定排序的有界列表查询。');

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
