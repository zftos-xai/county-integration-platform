-- 用途: 创建平台迁移、运行和HIS视图读取登录名，并绑定最小权限角色。
-- 使用方式: 复制为02-create-logins.local.sql后填写不同的强密码；本地文件不得提交。
-- 数据边界: 仅创建指定登录名及county_integration数据库用户，不访问HIS业务数据库。
-- 回退方案: 确认账号未被使用后删除数据库用户和服务器登录名；角色与数据库保留。

:setvar MigrationPassword "REPLACE_WITH_MIGRATION_SECRET"
:setvar AppPassword "REPLACE_WITH_APP_SECRET"
:setvar HisReaderPassword "REPLACE_WITH_HIS_READER_SECRET"

SET NOCOUNT ON;
SET XACT_ABORT ON;

IF N'$(MigrationPassword)' LIKE N'REPLACE_WITH_%'
   OR N'$(AppPassword)' LIKE N'REPLACE_WITH_%'
   OR N'$(HisReaderPassword)' LIKE N'REPLACE_WITH_%'
BEGIN
    RAISERROR(N'必须先在受保护的local.sql副本中填写三个不同的密码。', 16, 1);
    SET NOEXEC ON;
END;
GO

IF N'$(MigrationPassword)' = N'$(AppPassword)'
   OR N'$(MigrationPassword)' = N'$(HisReaderPassword)'
   OR N'$(AppPassword)' = N'$(HisReaderPassword)'
BEGIN
    RAISERROR(N'迁移、应用和HIS读取账号必须使用不同密码。', 16, 1);
    SET NOEXEC ON;
END;
GO

USE [master];
GO

IF SUSER_ID(N'platform_migration') IS NULL
BEGIN
    CREATE LOGIN [platform_migration]
        WITH PASSWORD = N'$(MigrationPassword)', CHECK_POLICY = ON, CHECK_EXPIRATION = ON;
END;
GO

IF SUSER_ID(N'platform_app') IS NULL
BEGIN
    CREATE LOGIN [platform_app]
        WITH PASSWORD = N'$(AppPassword)', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;
END;
GO

IF SUSER_ID(N'platform_his_reader') IS NULL
BEGIN
    CREATE LOGIN [platform_his_reader]
        WITH PASSWORD = N'$(HisReaderPassword)', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;
END;
GO

USE [county_integration];
GO

IF USER_ID(N'platform_migration') IS NULL
BEGIN
    CREATE USER [platform_migration] FOR LOGIN [platform_migration] WITH DEFAULT_SCHEMA = [dbo];
END;
ELSE
    ALTER USER [platform_migration] WITH DEFAULT_SCHEMA = [dbo];
GO

IF USER_ID(N'platform_app') IS NULL
BEGIN
    CREATE USER [platform_app] FOR LOGIN [platform_app] WITH DEFAULT_SCHEMA = [dbo];
END;
ELSE
    ALTER USER [platform_app] WITH DEFAULT_SCHEMA = [dbo];
GO

IF USER_ID(N'platform_his_reader') IS NULL
BEGIN
    CREATE USER [platform_his_reader] FOR LOGIN [platform_his_reader] WITH DEFAULT_SCHEMA = [dbo];
END;
ELSE
    ALTER USER [platform_his_reader] WITH DEFAULT_SCHEMA = [dbo];
GO

IF IS_ROLEMEMBER(N'db_ddladmin', N'platform_migration') <> 1
    ALTER ROLE [db_ddladmin] ADD MEMBER [platform_migration];
IF IS_ROLEMEMBER(N'db_datareader', N'platform_migration') <> 1
    ALTER ROLE [db_datareader] ADD MEMBER [platform_migration];
IF IS_ROLEMEMBER(N'db_datawriter', N'platform_migration') <> 1
    ALTER ROLE [db_datawriter] ADD MEMBER [platform_migration];
IF IS_ROLEMEMBER(N'platform_migration_role', N'platform_migration') <> 1
    ALTER ROLE [platform_migration_role] ADD MEMBER [platform_migration];

IF IS_ROLEMEMBER(N'platform_app_role', N'platform_app') <> 1
    ALTER ROLE [platform_app_role] ADD MEMBER [platform_app];

IF IS_ROLEMEMBER(N'platform_his_view_reader_role', N'platform_his_reader') <> 1
    ALTER ROLE [platform_his_view_reader_role] ADD MEMBER [platform_his_reader];
GO

SET NOEXEC OFF;
GO
