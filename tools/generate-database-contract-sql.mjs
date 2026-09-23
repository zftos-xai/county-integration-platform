import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { renderRepairSql } from './database-contract-repair-template.mjs'

const projectRoot = fileURLToPath(new URL('..', import.meta.url))
const migrationRoot = join(projectRoot, 'backend', 'src', 'main', 'resources', 'db', 'migration')
const outputFile = join(projectRoot, 'deploy', 'sqlserver', '04-verify-database-contract.sql')
const repairOutputFile = join(projectRoot, 'deploy', 'sqlserver', '05-repair-database-contract.sql')
const checkOnly = process.argv.includes('--check')
const createTablePattern = /^CREATE\s+TABLE\s+(?:(?:\[?([a-zA-Z_][\w]*)\]?)\.)?\[?([a-zA-Z_][\w]*)\]?\s*\((.*?)^\);/gims
const alterTableAddPattern = /^\s*ALTER\s+TABLE\s+(?:(?:\[?([a-zA-Z_][\w]*)\]?)\.)?\[?([a-zA-Z_][\w]*)\]?\s+ADD\s+([\s\S]*?);/gim
const alterTableColumnPattern = /^\s*ALTER\s+TABLE\s+(?:(?:\[?([a-zA-Z_][\w]*)\]?)\.)?\[?([a-zA-Z_][\w]*)\]?\s+ALTER\s+COLUMN\s+([^;\r\n]+);/gim
const columnPattern = /^\s*\[?([a-zA-Z_][\w]*)\]?\s+([a-zA-Z][a-zA-Z0-9_]*)(?:\((max|\d+)(?:\s*,\s*(\d+))?\))?(?:\s+(.*))?$/i

/** Escapes one Unicode SQL string literal. */
function sqlString(value) {
  return `N'${value.replaceAll("'", "''")}'`
}

/** Normalizes SQL Server type aliases used by the runtime catalog. */
function normalizeType(value) {
  const normalized = value.toLowerCase()
  return normalized === 'timestamp' ? 'rowversion' : normalized
}

/** Converts a nullable number to a SQL literal. */
function numberLiteral(value) {
  return value === null ? 'NULL' : String(value)
}

/** Renders expected schema rows shared by the verification and DDL repair scripts. */
function renderExpectedValues(columns) {
  return columns.map((column) => `    (${[
    sqlString(column.schemaName),
    sqlString(column.tableName),
    sqlString(column.columnName),
    sqlString(column.typeName),
    numberLiteral(column.maxLength),
    numberLiteral(column.precision),
    numberLiteral(column.scale),
    column.nullable ? 1 : 0,
    column.identity ? 1 : 0,
    column.computed ? 1 : 0,
  ].join(', ')})`).join(',\n')
}

/** Extracts the physical column contract from all versioned Flyway migrations. */
function parseMigrations() {
  const migrationFiles = readdirSync(migrationRoot)
    .filter((name) => /^V.*\.sql$/.test(name))
    .sort()
  const columns = []
  const columnIndexes = new Map()

  /** Parses one SQL Server column definition into the database-contract representation. */
  function parseColumn(migrationFile, schemaName, tableName, rawDefinition) {
    const definition = rawDefinition.replace(/--.*$/, '').trim().replace(/,\s*$/, '')
    const columnMatch = definition.match(columnPattern)
    if (!columnMatch) throw new Error(`${migrationFile} 无法解析字段定义: ${definition}`)

    const columnName = columnMatch[1].toLowerCase()
    const typeName = normalizeType(columnMatch[2])
    const firstArgument = columnMatch[3] ?? null
    const secondArgument = columnMatch[4] ?? null
    const remainder = columnMatch[5] ?? ''
    const lengthType = ['nvarchar', 'nchar', 'varchar', 'char', 'varbinary', 'binary'].includes(typeName)
    const numericType = ['decimal', 'numeric'].includes(typeName)
    const timeScaleType = ['datetime2', 'datetimeoffset', 'time'].includes(typeName)
    return {
      qualifiedName: `${schemaName}.${tableName}.${columnName}`,
      schemaName,
      tableName,
      columnName,
      typeName,
      maxLength: lengthType ? (firstArgument === 'max' ? -1 : Number(firstArgument) * (typeName.startsWith('n') ? 2 : 1)) : null,
      precision: numericType ? Number(firstArgument) : null,
      scale: numericType ? Number(secondArgument) : timeScaleType ? Number(firstArgument) : null,
      nullable: typeName !== 'rowversion' && !/\bNOT\s+NULL\b/i.test(remainder),
      identity: /\bIDENTITY\s*\(/i.test(remainder),
      computed: /\bAS\s*\(/i.test(remainder),
    }
  }

  /** Adds physical columns declared by a CREATE TABLE body or ALTER TABLE ... ADD body. */
  function appendColumns(migrationFile, schemaName, tableName, definitions) {
    for (const rawLine of definitions.split(/\r?\n/)) {
      const definition = rawLine.replace(/--.*$/, '').trim().replace(/,\s*$/, '')
      if (!definition || definition.toUpperCase().startsWith('CONSTRAINT')) continue
      const column = parseColumn(migrationFile, schemaName, tableName, definition)
      if (columnIndexes.has(column.qualifiedName)) throw new Error(`Flyway迁移重复定义字段 ${column.qualifiedName}`)
      columnIndexes.set(column.qualifiedName, columns.length)
      columns.push(column)
    }
  }

  /** Applies a later ALTER COLUMN declaration to the same physical field contract. */
  function alterColumn(migrationFile, schemaName, tableName, rawDefinition) {
    const altered = parseColumn(migrationFile, schemaName, tableName, rawDefinition)
    const index = columnIndexes.get(altered.qualifiedName)
    if (index === undefined) throw new Error(`${migrationFile} 修改了未声明字段 ${altered.qualifiedName}`)
    const previous = columns[index]
    if (previous.identity || previous.computed || altered.identity || altered.computed) {
      throw new Error(`${migrationFile} 不支持变更标识或计算字段 ${altered.qualifiedName}`)
    }
    columns[index] = altered
  }

  for (const migrationFile of migrationFiles) {
    const sql = readFileSync(join(migrationRoot, migrationFile), 'utf8')
    for (const tableMatch of sql.matchAll(createTablePattern)) {
      const schemaName = (tableMatch[1] ?? 'dbo').toLowerCase()
      const tableName = tableMatch[2].toLowerCase()
      appendColumns(migrationFile, schemaName, tableName, tableMatch[3])
    }
    for (const alterMatch of sql.matchAll(alterTableAddPattern)) {
      const schemaName = (alterMatch[1] ?? 'dbo').toLowerCase()
      const tableName = alterMatch[2].toLowerCase()
      const definitions = alterMatch[3]
      if (!definitions.trimStart().toUpperCase().startsWith('CONSTRAINT')) {
        appendColumns(migrationFile, schemaName, tableName, definitions)
      }
    }
    for (const alterMatch of sql.matchAll(alterTableColumnPattern)) {
      const schemaName = (alterMatch[1] ?? 'dbo').toLowerCase()
      const tableName = alterMatch[2].toLowerCase()
      alterColumn(migrationFile, schemaName, tableName, alterMatch[3])
    }
  }
  if (columns.length === 0) throw new Error('未从Flyway迁移脚本解析出任何字段')
  return columns
}

/** Renders one SQL Server 2012 compatible, read-only contract verification script. */
function renderSql(columns) {
  const values = renderExpectedValues(columns)

  return `-- 用途: 在SSMS或sqlcmd中直接执行，只读核对当前数据库与Flyway物理字段契约。\n-- 生成来源: backend/src/main/resources/db/migration/V*.sql。请勿手工维护预期字段清单。\n-- 重新生成: node tools/generate-database-contract-sql.mjs\n-- 数据边界: 不读取业务数据，不修改表、字段、约束、索引或数据。\n-- 判定原则: Flyway迁移是结构权威来源；数据库不一致标记为DATABASE，额外字段标记为MANUAL_REVIEW。\n\nUSE [county_integration];\nGO\n\nSET NOCOUNT ON;\nSET XACT_ABORT ON;\n\nDECLARE @expected_columns TABLE (\n    schema_name sysname NOT NULL,\n    table_name sysname NOT NULL,\n    column_name sysname NOT NULL,\n    type_name sysname NOT NULL,\n    max_length smallint NULL,\n    numeric_precision tinyint NULL,\n    numeric_scale tinyint NULL,\n    is_nullable bit NOT NULL,\n    is_identity bit NOT NULL,\n    is_computed bit NOT NULL,\n    PRIMARY KEY (schema_name, table_name, column_name)\n);\n\nINSERT INTO @expected_columns (\n    schema_name, table_name, column_name, type_name, max_length, numeric_precision,\n    numeric_scale, is_nullable, is_identity, is_computed\n)\nVALUES\n${values};\n\nDECLARE @violations TABLE (\n    error_code nvarchar(32) NOT NULL,\n    direction nvarchar(32) NOT NULL,\n    object_name nvarchar(776) NOT NULL,\n    expected_value nvarchar(4000) NOT NULL,\n    actual_value nvarchar(4000) NOT NULL,\n    recommendation nvarchar(4000) NOT NULL\n);\n\nDECLARE @product_version nvarchar(128);\nDECLARE @major_version int;\nDECLARE @compatibility_level int;\n\nSET @product_version = CONVERT(nvarchar(128), SERVERPROPERTY('ProductVersion'));\nSET @major_version = CONVERT(int, LEFT(@product_version, CHARINDEX(N'.', @product_version) - 1));\nSELECT @compatibility_level = [compatibility_level]\nFROM sys.databases\nWHERE [name] = DB_NAME();\n\nIF @major_version <> 11\nBEGIN\n    INSERT INTO @violations VALUES (\n        N'DBCONTRACT-E001', N'DATABASE', N'SQL Server实例版本', N'SQL Server 2012，主版本11',\n        @product_version, N'切换到医院批准的SQL Server 2012 SP4实例'\n    );\nEND;\n\nIF @compatibility_level <> 110\nBEGIN\n    INSERT INTO @violations VALUES (\n        N'DBCONTRACT-E002', N'DATABASE', N'数据库兼容级别', N'110',\n        CONVERT(nvarchar(20), @compatibility_level), N'由DBA评估后将当前平台数据库兼容级别调整为110'\n    );\nEND;\n\n;WITH actual_columns AS (\n    SELECT\n        table_schema.[name] AS schema_name,\n        business_table.[name] AS table_name,\n        business_column.[name] AS column_name,\n        CASE WHEN column_type.[name] = N'timestamp' THEN N'rowversion' ELSE column_type.[name] END AS type_name,\n        business_column.[max_length],\n        business_column.[precision] AS numeric_precision,\n        business_column.[scale] AS numeric_scale,\n        business_column.[is_nullable],\n        business_column.[is_identity],\n        business_column.[is_computed]\n    FROM sys.tables AS business_table\n    INNER JOIN sys.schemas AS table_schema ON table_schema.[schema_id] = business_table.[schema_id]\n    INNER JOIN sys.columns AS business_column ON business_column.[object_id] = business_table.[object_id]\n    INNER JOIN sys.types AS column_type ON column_type.[user_type_id] = business_column.[user_type_id]\n    WHERE EXISTS (\n        SELECT 1 FROM @expected_columns AS expected_table\n        WHERE expected_table.schema_name = table_schema.[name]\n          AND expected_table.table_name = business_table.[name]\n    )\n)\nINSERT INTO @violations\nSELECT\n    CASE\n        WHEN actual.column_name IS NULL THEN N'DBCONTRACT-E101'\n        WHEN expected.type_name <> actual.type_name THEN N'DBCONTRACT-E102'\n        WHEN expected.max_length IS NOT NULL AND expected.max_length <> actual.max_length THEN N'DBCONTRACT-E103'\n        WHEN expected.numeric_precision IS NOT NULL AND expected.numeric_precision <> actual.numeric_precision THEN N'DBCONTRACT-E104'\n        WHEN expected.numeric_scale IS NOT NULL AND expected.numeric_scale <> actual.numeric_scale THEN N'DBCONTRACT-E105'\n        WHEN expected.is_nullable <> actual.is_nullable THEN N'DBCONTRACT-E106'\n        WHEN expected.is_identity <> actual.is_identity THEN N'DBCONTRACT-E107'\n        ELSE N'DBCONTRACT-E108'\n    END,\n    N'DATABASE',\n    expected.schema_name + N'.' + expected.table_name + N'.' + expected.column_name,\n    N'type=' + expected.type_name\n        + N'; max_length=' + COALESCE(CONVERT(nvarchar(20), expected.max_length), N'N/A')\n        + N'; precision=' + COALESCE(CONVERT(nvarchar(20), expected.numeric_precision), N'N/A')\n        + N'; scale=' + COALESCE(CONVERT(nvarchar(20), expected.numeric_scale), N'N/A')\n        + N'; nullable=' + CONVERT(nvarchar(1), expected.is_nullable)\n        + N'; identity=' + CONVERT(nvarchar(1), expected.is_identity)\n        + N'; computed=' + CONVERT(nvarchar(1), expected.is_computed),\n    CASE WHEN actual.column_name IS NULL THEN N'字段不存在' ELSE\n        N'type=' + actual.type_name\n        + N'; max_length=' + CONVERT(nvarchar(20), actual.max_length)\n        + N'; precision=' + CONVERT(nvarchar(20), actual.numeric_precision)\n        + N'; scale=' + CONVERT(nvarchar(20), actual.numeric_scale)\n        + N'; nullable=' + CONVERT(nvarchar(1), actual.is_nullable)\n        + N'; identity=' + CONVERT(nvarchar(1), actual.is_identity)\n        + N'; computed=' + CONVERT(nvarchar(1), actual.is_computed) END,\n    N'核对需求后通过正式Flyway迁移修正数据库；不要只修改Mapper或Java类型掩盖物理结构差异'\nFROM @expected_columns AS expected\nLEFT JOIN actual_columns AS actual\n    ON actual.schema_name = expected.schema_name\n   AND actual.table_name = expected.table_name\n   AND actual.column_name = expected.column_name\nWHERE actual.column_name IS NULL\n   OR expected.type_name <> actual.type_name\n   OR (expected.max_length IS NOT NULL AND expected.max_length <> actual.max_length)\n   OR (expected.numeric_precision IS NOT NULL AND expected.numeric_precision <> actual.numeric_precision)\n   OR (expected.numeric_scale IS NOT NULL AND expected.numeric_scale <> actual.numeric_scale)\n   OR expected.is_nullable <> actual.is_nullable\n   OR expected.is_identity <> actual.is_identity\n   OR expected.is_computed <> actual.is_computed;\n\n;WITH expected_tables AS (\n    SELECT DISTINCT schema_name, table_name FROM @expected_columns\n+)\nINSERT INTO @violations\nSELECT\n    N'DBCONTRACT-E109', N'MANUAL_REVIEW',\n    table_schema.[name] + N'.' + business_table.[name] + N'.' + business_column.[name],\n    N'当前Flyway迁移中不存在该字段',\n    N'type=' + column_type.[name] + N'; max_length=' + CONVERT(nvarchar(20), business_column.[max_length]),\n    N'确认是否为未经Flyway执行的人工改表；需要保留时新增正式迁移和契约依据'\nFROM sys.tables AS business_table\nINNER JOIN sys.schemas AS table_schema ON table_schema.[schema_id] = business_table.[schema_id]\nINNER JOIN sys.columns AS business_column ON business_column.[object_id] = business_table.[object_id]\nINNER JOIN sys.types AS column_type ON column_type.[user_type_id] = business_column.[user_type_id]\nINNER JOIN expected_tables AS expected_table\n    ON expected_table.schema_name = table_schema.[name]\n   AND expected_table.table_name = business_table.[name]\nLEFT JOIN @expected_columns AS expected\n    ON expected.schema_name = table_schema.[name]\n   AND expected.table_name = business_table.[name]\n   AND expected.column_name = business_column.[name]\nWHERE expected.column_name IS NULL;\n\nSELECT\n    error_code, direction, object_name, expected_value, actual_value, recommendation\nFROM @violations\nORDER BY object_name, error_code;\n\nIF EXISTS (SELECT 1 FROM @violations)\nBEGIN\n    DECLARE @violation_count int;\n    DECLARE @failure_message nvarchar(2048);\n    SELECT @violation_count = COUNT(*) FROM @violations;\n    SET @failure_message = N'数据库契约检查失败，共发现 ' + CONVERT(nvarchar(20), @violation_count)\n        + N' 项差异；请按结果集中的 direction 和 recommendation 处理。';\n    RAISERROR(@failure_message, 16, 1);\nEND;\nELSE\nBEGIN\n    SELECT\n        N'PASS' AS contract_status,\n        @product_version AS product_version,\n        @compatibility_level AS compatibility_level,\n        (SELECT COUNT(*) FROM @expected_columns) AS verified_column_count,\n        N'数据库物理字段与Flyway契约一致；Mapper和Java模型仍由应用启动检查负责。' AS message;\nEND;\nGO\n`
}

const columns = parseMigrations()
const rendered = renderSql(columns)
const repairRendered = renderRepairSql(renderExpectedValues(columns))
if (checkOnly) {
  const staleFiles = [
    [outputFile, rendered],
    [repairOutputFile, repairRendered],
  ].filter(([file, expected]) => !existsSync(file) || readFileSync(file, 'utf8') !== expected)
  if (staleFiles.length > 0) {
    console.error(`${staleFiles.map(([file]) => relative(projectRoot, file)).join('、')} 已过期，请执行 node tools/generate-database-contract-sql.mjs`)
    process.exit(1)
  }
  console.log('数据库契约核查及DDL修复脚本与Flyway迁移一致。')
} else {
  writeFileSync(outputFile, rendered, 'utf8')
  writeFileSync(repairOutputFile, repairRendered, 'utf8')
  console.log(`已生成 ${relative(projectRoot, outputFile)} 和 ${relative(projectRoot, repairOutputFile)}`)
}
