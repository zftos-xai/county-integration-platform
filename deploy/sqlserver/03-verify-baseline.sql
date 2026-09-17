-- 用途: 只读核对SQL Server版本、平台数据库、登录映射和角色成员关系。
-- 数据边界: 不输出密码，不访问HIS业务数据库，不修改任何数据库对象。

SET NOCOUNT ON;

SELECT
    CONVERT(nvarchar(128), SERVERPROPERTY('MachineName')) AS machine_name,
    CONVERT(nvarchar(128), SERVERPROPERTY('InstanceName')) AS instance_name,
    CONVERT(nvarchar(128), SERVERPROPERTY('ProductVersion')) AS product_version,
    CONVERT(nvarchar(128), SERVERPROPERTY('ProductLevel')) AS product_level,
    CONVERT(nvarchar(128), SERVERPROPERTY('Edition')) AS edition;

SELECT
    [name] AS database_name,
    [compatibility_level],
    [state_desc],
    [recovery_model_desc]
FROM sys.databases
WHERE [name] = N'county_integration';

SELECT
    [name] AS login_name,
    [type_desc],
    [is_disabled],
    [is_policy_checked],
    [is_expiration_checked]
FROM sys.sql_logins
WHERE [name] IN (N'platform_migration', N'platform_app', N'platform_his_reader')
ORDER BY [name];

USE [county_integration];

SELECT
    role_principal.[name] AS role_name,
    member_principal.[name] AS member_name
FROM sys.database_role_members AS role_member
INNER JOIN sys.database_principals AS role_principal
    ON role_principal.[principal_id] = role_member.[role_principal_id]
INNER JOIN sys.database_principals AS member_principal
    ON member_principal.[principal_id] = role_member.[member_principal_id]
WHERE member_principal.[name] IN (N'platform_migration', N'platform_app', N'platform_his_reader')
ORDER BY member_principal.[name], role_principal.[name];

IF OBJECT_ID(N'dbo.flyway_schema_history', N'U') IS NOT NULL
BEGIN
    EXEC (
        N'SELECT
              [version],
              [description],
              [type],
              [installed_on],
              [success]
          FROM [dbo].[flyway_schema_history]
          ORDER BY [installed_rank];'
    );
END;
ELSE
BEGIN
    SELECT N'Flyway尚未执行，未发现dbo.flyway_schema_history。' AS flyway_status;
END;
GO

-- 输出平台业务表的中文名称；MS_Description为空表示主版本不合格。
SELECT
    table_schema.[name] AS schema_name,
    business_table.[name] AS table_name,
    CONVERT(nvarchar(4000), table_description.[value]) AS table_description
FROM sys.tables AS business_table
INNER JOIN sys.schemas AS table_schema
    ON table_schema.[schema_id] = business_table.[schema_id]
LEFT JOIN sys.extended_properties AS table_description
    ON table_description.[class] = 1
   AND table_description.[major_id] = business_table.[object_id]
   AND table_description.[minor_id] = 0
   AND table_description.[name] = N'MS_Description'
WHERE table_schema.[name] = N'dbo'
  AND business_table.[name] <> N'flyway_schema_history'
ORDER BY business_table.[name];

-- 输出平台业务字段的中文名称，便于SSMS外的数据字典核对。
SELECT
    business_table.[name] AS table_name,
    business_column.[column_id],
    business_column.[name] AS column_name,
    CONVERT(nvarchar(4000), column_description.[value]) AS column_description
FROM sys.tables AS business_table
INNER JOIN sys.schemas AS table_schema
    ON table_schema.[schema_id] = business_table.[schema_id]
INNER JOIN sys.columns AS business_column
    ON business_column.[object_id] = business_table.[object_id]
LEFT JOIN sys.extended_properties AS column_description
    ON column_description.[class] = 1
   AND column_description.[major_id] = business_column.[object_id]
   AND column_description.[minor_id] = business_column.[column_id]
   AND column_description.[name] = N'MS_Description'
WHERE table_schema.[name] = N'dbo'
  AND business_table.[name] <> N'flyway_schema_history'
ORDER BY business_table.[name], business_column.[column_id];

IF EXISTS (
    SELECT 1
    FROM sys.tables AS business_table
    INNER JOIN sys.schemas AS table_schema
        ON table_schema.[schema_id] = business_table.[schema_id]
    LEFT JOIN sys.extended_properties AS table_description
        ON table_description.[class] = 1
       AND table_description.[major_id] = business_table.[object_id]
       AND table_description.[minor_id] = 0
       AND table_description.[name] = N'MS_Description'
    WHERE table_schema.[name] = N'dbo'
      AND business_table.[name] <> N'flyway_schema_history'
      AND table_description.[value] IS NULL
)
BEGIN
    RAISERROR(N'存在缺少中文表注释的业务表。', 16, 1);
END;

IF EXISTS (
    SELECT 1
    FROM sys.tables AS business_table
    INNER JOIN sys.schemas AS table_schema
        ON table_schema.[schema_id] = business_table.[schema_id]
    INNER JOIN sys.columns AS business_column
        ON business_column.[object_id] = business_table.[object_id]
    LEFT JOIN sys.extended_properties AS column_description
        ON column_description.[class] = 1
       AND column_description.[major_id] = business_column.[object_id]
       AND column_description.[minor_id] = business_column.[column_id]
       AND column_description.[name] = N'MS_Description'
    WHERE table_schema.[name] = N'dbo'
      AND business_table.[name] <> N'flyway_schema_history'
      AND column_description.[value] IS NULL
)
BEGIN
    RAISERROR(N'存在缺少中文字段注释的业务字段。', 16, 1);
END;
GO
