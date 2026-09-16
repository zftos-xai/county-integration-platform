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
