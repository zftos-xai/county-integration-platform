/**
 * Renders a SQL Server 2012 compatible transactional DDL repair script.
 *
 * @param {string} expectedValuesSql generated VALUES rows for the expected schema
 * @returns {string} standalone SQL script
 */
export function renderRepairSql(expectedValuesSql) {
  return `-- 用途: 在已备份并完成变更审批后，执行DDL修复数据库与Flyway契约之间的可确定字段差异。
-- 生成来源: backend/src/main/resources/db/migration/V*.sql。请勿手工维护预期字段清单。
-- 重新生成: node tools/generate-database-contract-sql.mjs
-- 自动修改: 现有普通字段的字符/二进制长度和NULL属性；所有DDL在同一事务中执行。
-- 拒绝修改: 不自动增删字段，不改变类型、数值/时间精度、IDENTITY或计算属性，不拆除索引和约束。
-- 执行要求: 必须先备份并在SQL Server 2012 SP4测试实例演练；执行结果不能替代正式Flyway迁移记录。

USE [county_integration];
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @expected_columns TABLE (
    schema_name sysname NOT NULL,
    table_name sysname NOT NULL,
    column_name sysname NOT NULL,
    type_name sysname NOT NULL,
    max_length smallint NULL,
    numeric_precision tinyint NULL,
    numeric_scale tinyint NULL,
    is_nullable bit NOT NULL,
    is_identity bit NOT NULL,
    is_computed bit NOT NULL,
    PRIMARY KEY (schema_name, table_name, column_name)
);

INSERT INTO @expected_columns (
    schema_name, table_name, column_name, type_name, max_length, numeric_precision,
    numeric_scale, is_nullable, is_identity, is_computed
)
VALUES
${expectedValuesSql};

CREATE TABLE #blockers (
    error_code nvarchar(32) NOT NULL,
    object_name nvarchar(776) NOT NULL,
    reason nvarchar(4000) NOT NULL,
    required_action nvarchar(4000) NOT NULL
);

DECLARE @ddl_plan TABLE (
    execution_order int IDENTITY(1,1) NOT NULL PRIMARY KEY,
    object_name nvarchar(776) NOT NULL,
    ddl_statement nvarchar(max) NOT NULL
);

DECLARE @product_version nvarchar(128);
DECLARE @major_version int;
DECLARE @compatibility_level int;
SET @product_version = CONVERT(nvarchar(128), SERVERPROPERTY('ProductVersion'));
SET @major_version = CONVERT(int, LEFT(@product_version, CHARINDEX(N'.', @product_version) - 1));
SELECT @compatibility_level = [compatibility_level]
FROM sys.databases
WHERE [name] = DB_NAME();

IF @major_version <> 11
    INSERT INTO #blockers VALUES (
        N'DBCONTRACT-E001', N'SQL Server实例版本', N'实际版本为' + @product_version,
        N'切换到医院批准的SQL Server 2012 SP4实例'
    );

IF @compatibility_level <> 110
    INSERT INTO #blockers VALUES (
        N'DBCONTRACT-E002', N'数据库兼容级别',
        N'实际兼容级别为' + CONVERT(nvarchar(20), @compatibility_level),
        N'由DBA评估后调整为110'
    );

;WITH actual_columns AS (
    SELECT
        table_schema.[name] AS schema_name,
        business_table.[name] AS table_name,
        business_column.[name] AS column_name,
        CASE WHEN column_type.[name] = N'timestamp' THEN N'rowversion' ELSE column_type.[name] END AS type_name,
        business_column.[max_length],
        business_column.[precision] AS numeric_precision,
        business_column.[scale] AS numeric_scale,
        business_column.[is_nullable],
        business_column.[is_identity],
        business_column.[is_computed]
    FROM sys.tables AS business_table
    INNER JOIN sys.schemas AS table_schema ON table_schema.[schema_id] = business_table.[schema_id]
    INNER JOIN sys.columns AS business_column ON business_column.[object_id] = business_table.[object_id]
    INNER JOIN sys.types AS column_type ON column_type.[user_type_id] = business_column.[user_type_id]
)
INSERT INTO #blockers
SELECT
    CASE
        WHEN actual.column_name IS NULL THEN N'DBCONTRACT-E301'
        WHEN expected.type_name <> actual.type_name THEN N'DBCONTRACT-E302'
        WHEN (expected.numeric_precision IS NOT NULL
              AND (expected.numeric_precision <> actual.numeric_precision
                   OR expected.numeric_scale <> actual.numeric_scale))
             OR (expected.numeric_precision IS NULL AND expected.numeric_scale IS NOT NULL
                 AND expected.numeric_scale <> actual.numeric_scale) THEN N'DBCONTRACT-E303'
        ELSE N'DBCONTRACT-E304'
    END,
    expected.schema_name + N'.' + expected.table_name + N'.' + expected.column_name,
    CASE
        WHEN actual.column_name IS NULL THEN N'字段不存在'
        WHEN expected.type_name <> actual.type_name THEN N'字段类型不同，自动转换可能丢失数据'
        WHEN (expected.numeric_precision IS NOT NULL
              AND (expected.numeric_precision <> actual.numeric_precision
                   OR expected.numeric_scale <> actual.numeric_scale))
             OR (expected.numeric_precision IS NULL AND expected.numeric_scale IS NOT NULL
                 AND expected.numeric_scale <> actual.numeric_scale)
            THEN N'数值或时间精度不同，自动转换可能舍入、溢出或改变时间值'
        ELSE N'IDENTITY或计算字段属性不同，不能通过普通ALTER COLUMN安全修正'
    END,
    N'编写归属正确业务主版本的正式Flyway迁移，并包含数据预检、约束处理和回退方案'
FROM @expected_columns AS expected
LEFT JOIN actual_columns AS actual
    ON actual.schema_name = expected.schema_name
   AND actual.table_name = expected.table_name
   AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL
   OR expected.type_name <> actual.type_name
   OR (expected.numeric_precision IS NOT NULL
       AND (expected.numeric_precision <> actual.numeric_precision
            OR expected.numeric_scale <> actual.numeric_scale))
   OR (expected.numeric_precision IS NULL AND expected.numeric_scale IS NOT NULL
       AND expected.numeric_scale <> actual.numeric_scale)
   OR expected.is_identity <> actual.is_identity
   OR expected.is_computed <> actual.is_computed;

;WITH expected_tables AS (
    SELECT DISTINCT schema_name, table_name FROM @expected_columns
)
INSERT INTO #blockers
SELECT
    N'DBCONTRACT-E305',
    table_schema.[name] + N'.' + business_table.[name] + N'.' + business_column.[name],
    N'数据库存在Flyway契约未声明的额外字段',
    N'禁止自动DROP COLUMN；查明来源后编写正式Flyway迁移或恢复正确数据库'
FROM sys.tables AS business_table
INNER JOIN sys.schemas AS table_schema ON table_schema.[schema_id] = business_table.[schema_id]
INNER JOIN sys.columns AS business_column ON business_column.[object_id] = business_table.[object_id]
INNER JOIN expected_tables AS expected_table
    ON expected_table.schema_name = table_schema.[name]
   AND expected_table.table_name = business_table.[name]
LEFT JOIN @expected_columns AS expected
    ON expected.schema_name = table_schema.[name]
   AND expected.table_name = business_table.[name]
   AND expected.column_name = business_column.[name]
WHERE expected.column_name IS NULL;

-- 缩短长度或收紧NOT NULL之前检查真实数据；不兼容时停止全部DDL。
DECLARE @precheck_sql nvarchar(max);
SET @precheck_sql = N'';
SELECT @precheck_sql = @precheck_sql
    + CASE WHEN expected.max_length IS NOT NULL
                 AND expected.max_length <> -1
                 AND (actual.max_length = -1 OR expected.max_length < actual.max_length)
        THEN N'IF EXISTS (SELECT 1 FROM '
             + QUOTENAME(expected.schema_name) + N'.' + QUOTENAME(expected.table_name)
             + N' WHERE DATALENGTH(' + QUOTENAME(expected.column_name) + N') > '
             + CONVERT(nvarchar(20), expected.max_length) + N') '
             + N'INSERT INTO #blockers VALUES (N''DBCONTRACT-E306'', N'''
             + REPLACE(expected.schema_name + N'.' + expected.table_name + N'.' + expected.column_name, N'''', N'''''')
             + N''', N''现有数据超过目标字段长度'', N''先清理或迁移超长数据，再执行DDL修复'');'
        ELSE N'' END
    + CASE WHEN expected.is_nullable = 0 AND actual.is_nullable = 1
        THEN N'IF EXISTS (SELECT 1 FROM '
             + QUOTENAME(expected.schema_name) + N'.' + QUOTENAME(expected.table_name)
             + N' WHERE ' + QUOTENAME(expected.column_name) + N' IS NULL) '
             + N'INSERT INTO #blockers VALUES (N''DBCONTRACT-E307'', N'''
             + REPLACE(expected.schema_name + N'.' + expected.table_name + N'.' + expected.column_name, N'''', N'''''')
             + N''', N''现有数据包含NULL，不能直接收紧为NOT NULL'', N''先按业务依据修复空值，再执行DDL修复'');'
        ELSE N'' END
FROM @expected_columns AS expected
INNER JOIN (
    SELECT
        table_schema.[name] AS schema_name,
        business_table.[name] AS table_name,
        business_column.[name] AS column_name,
        CASE WHEN column_type.[name] = N'timestamp' THEN N'rowversion' ELSE column_type.[name] END AS type_name,
        business_column.[max_length],
        business_column.[is_nullable]
    FROM sys.tables AS business_table
    INNER JOIN sys.schemas AS table_schema ON table_schema.[schema_id] = business_table.[schema_id]
    INNER JOIN sys.columns AS business_column ON business_column.[object_id] = business_table.[object_id]
    INNER JOIN sys.types AS column_type ON column_type.[user_type_id] = business_column.[user_type_id]
) AS actual
    ON actual.schema_name = expected.schema_name
   AND actual.table_name = expected.table_name
   AND actual.column_name = expected.column_name
WHERE expected.type_name = actual.type_name;

EXEC sys.sp_executesql @precheck_sql;

IF EXISTS (SELECT 1 FROM #blockers)
BEGIN
    SELECT error_code, object_name, reason, required_action
    FROM #blockers
    ORDER BY object_name, error_code;
    DROP TABLE #blockers;
    RAISERROR(N'DDL修复已取消：存在不能安全自动处理的契约差异，数据库未发生修改。', 16, 1);
    RETURN;
END;

;WITH actual_columns AS (
    SELECT
        table_schema.[name] AS schema_name,
        business_table.[name] AS table_name,
        business_column.[name] AS column_name,
        CASE WHEN column_type.[name] = N'timestamp' THEN N'rowversion' ELSE column_type.[name] END AS type_name,
        business_column.[max_length],
        business_column.[is_nullable],
        business_column.[is_identity],
        business_column.[is_computed]
    FROM sys.tables AS business_table
    INNER JOIN sys.schemas AS table_schema ON table_schema.[schema_id] = business_table.[schema_id]
    INNER JOIN sys.columns AS business_column ON business_column.[object_id] = business_table.[object_id]
    INNER JOIN sys.types AS column_type ON column_type.[user_type_id] = business_column.[user_type_id]
)
INSERT INTO @ddl_plan (object_name, ddl_statement)
SELECT
    expected.schema_name + N'.' + expected.table_name + N'.' + expected.column_name,
    N'ALTER TABLE ' + QUOTENAME(expected.schema_name) + N'.' + QUOTENAME(expected.table_name)
        + N' ALTER COLUMN ' + QUOTENAME(expected.column_name) + N' '
        + expected.type_name
        + CASE
              WHEN expected.max_length IS NULL THEN N''
              WHEN expected.max_length = -1 THEN N'(max)'
              WHEN expected.type_name IN (N'nvarchar', N'nchar')
                  THEN N'(' + CONVERT(nvarchar(20), expected.max_length / 2) + N')'
              ELSE N'(' + CONVERT(nvarchar(20), expected.max_length) + N')'
          END
        + CASE WHEN expected.is_nullable = 1 THEN N' NULL;' ELSE N' NOT NULL;' END
FROM @expected_columns AS expected
INNER JOIN actual_columns AS actual
    ON actual.schema_name = expected.schema_name
   AND actual.table_name = expected.table_name
   AND actual.column_name = expected.column_name
WHERE expected.type_name = actual.type_name
  AND expected.is_identity = actual.is_identity
  AND expected.is_computed = actual.is_computed
  AND ((expected.max_length IS NOT NULL AND expected.max_length <> actual.max_length)
       OR expected.is_nullable <> actual.is_nullable);

SELECT execution_order, object_name, ddl_statement
FROM @ddl_plan
ORDER BY execution_order;

IF NOT EXISTS (SELECT 1 FROM @ddl_plan)
BEGIN
    SELECT N'PASS' AS repair_status, N'数据库字段无需执行DDL修复。' AS message;
    DROP TABLE #blockers;
    RETURN;
END;

DECLARE @ddl nvarchar(max);
DECLARE ddl_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT ddl_statement FROM @ddl_plan ORDER BY execution_order;

BEGIN TRY
    BEGIN TRANSACTION;
    OPEN ddl_cursor;
    FETCH NEXT FROM ddl_cursor INTO @ddl;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        PRINT @ddl;
        EXEC sys.sp_executesql @ddl;
        FETCH NEXT FROM ddl_cursor INTO @ddl;
    END;
    CLOSE ddl_cursor;
    DEALLOCATE ddl_cursor;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF CURSOR_STATUS('local', 'ddl_cursor') >= 0 CLOSE ddl_cursor;
    IF CURSOR_STATUS('local', 'ddl_cursor') > -3 DEALLOCATE ddl_cursor;
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    DECLARE @ddl_error nvarchar(2048);
    SET @ddl_error = N'DDL修复失败并已回滚：' + ERROR_MESSAGE();
    DROP TABLE #blockers;
    RAISERROR(@ddl_error, 16, 1);
    RETURN;
END CATCH;

SELECT
    N'APPLIED' AS repair_status,
    COUNT(*) AS applied_ddl_count,
    N'DDL修复已提交；请立即重新执行04-verify-database-contract.sql并保留变更记录。' AS message
FROM @ddl_plan;

DROP TABLE #blockers;
GO
`
}
