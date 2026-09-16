-- 业务域: organization
-- 主版本: V0200
-- 脚本类型: MAIN
-- 变更说明: 建立平台机构档案、层级、启停和并发控制基线
-- 需求依据: 项目开发PRD第4章、开发批次任务计划DEV-005
-- 数据边界: 仅保存平台管理机构，不保存100-008上游机构原始数据或医疗业务正文
-- 时间口径: valid_from、valid_to、created_at和updated_at保存UTC时间
-- 回退方案: 首次部署失败时删除本脚本建立的索引、外键、约束和表；共享环境执行前由DBA复核

-- 表用途: 保存平台权限、用户归属和配置作用域使用的受控机构档案；不承载上游机构同步原始记录
CREATE TABLE org_organization (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, -- 平台内部机构主键
    organization_code NVARCHAR(64) NOT NULL, -- 平台统一、稳定且对外使用的机构编码
    organization_name NVARCHAR(200) NOT NULL, -- 管理界面和审计展示使用的机构名称
    organization_type NVARCHAR(32) NOT NULL, -- 经项目确认的机构类型代码；未在本脚本预置虚构枚举
    parent_id BIGINT NULL, -- 可选父机构主键；空值表示顶级机构
    is_enabled BIT NOT NULL CONSTRAINT df_org_organization_enabled DEFAULT 1, -- 机构是否可用；停用不删除历史档案
    valid_from DATETIME2(3) NULL, -- 可选业务有效起始UTC时间；空值表示未限定起始时间
    valid_to DATETIME2(3) NULL, -- 可选业务有效结束UTC时间；空值表示未限定结束时间
    created_by NVARCHAR(64) NOT NULL, -- 创建人稳定主体标识快照；安全引导期不强制外键
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_org_organization_created DEFAULT SYSUTCDATETIME(), -- 机构档案创建UTC时间
    updated_by NVARCHAR(64) NOT NULL, -- 最后修改人稳定主体标识快照
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_org_organization_updated DEFAULT SYSUTCDATETIME(), -- 机构档案最后修改UTC时间
    row_version ROWVERSION, -- SQL Server并发版本，由客户端回传以防止无感知覆盖
    CONSTRAINT uq_org_organization_code UNIQUE (organization_code), -- 保证平台机构编码唯一
    CONSTRAINT fk_org_organization_parent FOREIGN KEY (parent_id) REFERENCES org_organization(id), -- 保证父机构存在；循环由应用层阻断
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
