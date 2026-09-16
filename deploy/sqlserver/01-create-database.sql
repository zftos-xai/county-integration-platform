-- 用途: 创建县域中间接口平台独立数据库及最小权限角色。
-- 适用环境: Microsoft SQL Server 2012 SP4，数据库兼容级别110。
-- 数据边界: 仅操作county_integration数据库，不访问或修改HIS业务数据库。
-- 回退方案: 数据库尚无业务数据时由DBA确认目标后删除该独立数据库；有数据后必须走备份恢复和变更审批。

SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @major_version int;
SET @major_version = CONVERT(int, PARSENAME(CONVERT(nvarchar(128), SERVERPROPERTY('ProductVersion')), 4));

IF @major_version <> 11
BEGIN
    RAISERROR(N'目标实例不是 SQL Server 2012（主版本11），停止建库。', 16, 1);
    RETURN;
END;

IF DB_ID(N'county_integration') IS NULL
BEGIN
    EXEC (N'CREATE DATABASE [county_integration]');
END;
GO

ALTER DATABASE [county_integration] SET COMPATIBILITY_LEVEL = 110;
GO

USE [county_integration];
GO

-- 迁移角色：仅发布窗口使用，成员负责执行Flyway版本化DDL和必要的数据迁移。
IF NOT EXISTS (
    SELECT 1
    FROM sys.database_principals
    WHERE [name] = N'platform_migration_role'
      AND [type] = N'R'
)
BEGIN
    CREATE ROLE [platform_migration_role] AUTHORIZATION [dbo];
END;
GO

-- 应用角色：后端运行时只访问平台dbo架构，不具备数据库结构修改权限。
IF NOT EXISTS (
    SELECT 1
    FROM sys.database_principals
    WHERE [name] = N'platform_app_role'
      AND [type] = N'R'
)
BEGIN
    CREATE ROLE [platform_app_role] AUTHORIZATION [dbo];
END;
GO

-- HIS视图读取角色：初始不授予对象权限，批准视图建立后再逐个授予SELECT。
IF NOT EXISTS (
    SELECT 1
    FROM sys.database_principals
    WHERE [name] = N'platform_his_view_reader_role'
      AND [type] = N'R'
)
BEGIN
    CREATE ROLE [platform_his_view_reader_role] AUTHORIZATION [dbo];
END;
GO

GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE ON SCHEMA::[dbo] TO [platform_app_role];
GO
