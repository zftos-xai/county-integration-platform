import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const projectRoot = resolve(fileURLToPath(new URL('..', import.meta.url)))
const migrationDirectory = resolve(projectRoot, 'backend/src/main/resources/db/migration')
const maximumPatchCount = 5
const mainPattern = /^V(\d{4})__([a-z][a-z0-9_]*)_main\.sql$/
const patchPattern = /^V(\d{4})__([a-z][a-z0-9_]*)_V(\d{4})_patch_(\d{3})_([a-z][a-z0-9_]*)\.sql$/
const files = readdirSync(migrationDirectory).filter(file => file.endsWith('.sql')).sort()
const errors = []
const versions = new Map()
const mainVersions = new Map()
const patchesByBusiness = new Map()
const createdObjects = []
const describedObjects = new Map()

if (files.length === 0) {
  errors.push('migration目录中没有业务主版本SQL')
}

function report(file, message) {
  errors.push(`${file}: ${message}`)
}

function requireHeader(file, sql, label, expectedValue) {
  const expression = new RegExp(`^-- ${label}:\\s*(.+)$`, 'm')
  const match = sql.match(expression)
  if (!match) {
    report(file, `缺少脚本头“-- ${label}: ...”`)
    return
  }
  if (expectedValue && match[1].trim() !== expectedValue) {
    report(file, `脚本头“${label}”应为“${expectedValue}”`)
  }
}

function checkDocumentedObjects(file, sql) {
  const tablePattern = /CREATE\s+TABLE\s+([a-z][a-z0-9_]*)\s*\(([\s\S]*?)\);/gi
  for (const tableMatch of sql.matchAll(tablePattern)) {
    const tableName = tableMatch[1].toLowerCase()
    createdObjects.push({ file, type: 'TABLE', table: tableName, object: null })
    const beforeTable = sql.slice(Math.max(0, tableMatch.index - 300), tableMatch.index)
    if (!/-- 表中文名称:\s*[\u3400-\u9fff]+表\s*$/m.test(beforeTable)) {
      report(file, `表 ${tableMatch[1]} 前缺少以“表”结尾的“-- 表中文名称: ...”`)
    }
    if (!/-- 表用途:\s*\S+/.test(beforeTable)) {
      report(file, `表 ${tableMatch[1]} 前缺少“-- 表用途: ...”`)
    }
    for (const rawLine of tableMatch[2].split(/\r?\n/)) {
      const line = rawLine.trim()
      if (!line || line.startsWith('--')) continue
      const isConstraint = /^CONSTRAINT\s+/i.test(line)
      const isField = !isConstraint && /^[a-z][a-z0-9_]*\s+/i.test(line)
      if ((isField || isConstraint) && !line.includes('--')) {
        report(file, `字段或约束缺少行内说明：“${line}”`)
      }
      if (isField) {
        const fieldName = line.match(/^([a-z][a-z0-9_]*)\s+/i)[1].toLowerCase()
        createdObjects.push({ file, type: 'COLUMN', table: tableName, object: fieldName })
        if (/\bPRIMARY\s+KEY\b/i.test(line) && !/\bCONSTRAINT\s+[a-z][a-z0-9_]*\s+PRIMARY\s+KEY\b/i.test(line)) {
          createdObjects.push({ file, type: 'CONSTRAINT', table: tableName, object: '$primary_key' })
          report(file, `表 ${tableName} 的主键必须使用CONSTRAINT显式命名`)
        }
      }
      const constraintMatch = line.match(/\bCONSTRAINT\s+([a-z][a-z0-9_]*)\s+(PRIMARY\s+KEY|UNIQUE|CHECK|FOREIGN\s+KEY|DEFAULT)/i)
      if (constraintMatch) {
        createdObjects.push({ file, type: 'CONSTRAINT', table: tableName, object: constraintMatch[1].toLowerCase() })
      }
    }
  }

  // 增量迁移的字段和约束同样必须有数据库元数据说明。
  const addedColumnsPattern = /ALTER\s+TABLE\s+(?:dbo\.)?([a-z][a-z0-9_]*)\s+ADD\s*\n([\s\S]*?);/gi
  for (const match of file.includes('_patch_') ? sql.matchAll(addedColumnsPattern) : []) {
    const tableName = match[1].toLowerCase()
    for (const rawLine of match[2].split(/\r?\n/)) {
      const line = rawLine.trim()
      const field = line.match(/^([a-z][a-z0-9_]*)\s+(?:N?VARCHAR|VARBINARY|BIGINT|INT|BIT|DATETIME2)\b/i)
      if (!field) continue
      createdObjects.push({ file, type: 'COLUMN', table: tableName, object: field[1].toLowerCase() })
      if (!line.includes('--')) report(file, `增量字段缺少行内说明：“${line}”`)
      const defaultConstraint = line.match(/\bCONSTRAINT\s+([a-z][a-z0-9_]*)\s+DEFAULT\b/i)
      if (defaultConstraint) {
        createdObjects.push({ file, type: 'CONSTRAINT', table: tableName, object: defaultConstraint[1].toLowerCase() })
      }
    }
  }

  const addedConstraintPattern = /ALTER\s+TABLE\s+(?:dbo\.)?([a-z][a-z0-9_]*)\s+WITH\s+CHECK\s+ADD\s+CONSTRAINT\s+([a-z][a-z0-9_]*)/gi
  for (const match of file.includes('_patch_') ? sql.matchAll(addedConstraintPattern) : []) {
    createdObjects.push({ file, type: 'CONSTRAINT', table: match[1].toLowerCase(), object: match[2].toLowerCase() })
  }

  const indexPattern = /CREATE\s+(?:UNIQUE\s+)?INDEX\s+([a-z][a-z0-9_]*)/gi
  for (const indexMatch of sql.matchAll(indexPattern)) {
    const afterIndex = sql.slice(indexMatch.index)
    const tableMatch = afterIndex.match(/\bON\s+(?:dbo\.)?([a-z][a-z0-9_]*)\s*\(/i)
    if (tableMatch) {
      createdObjects.push({ file, type: 'INDEX', table: tableMatch[1].toLowerCase(), object: indexMatch[1].toLowerCase() })
    }
    const beforeIndex = sql.slice(Math.max(0, indexMatch.index - 300), indexMatch.index)
    if (!/-- 索引用途:\s*\S+/.test(beforeIndex)) {
      report(file, `索引 ${indexMatch[1]} 前缺少“-- 索引用途: ...”`)
    }
  }
}

function collectPersistedDescriptions(file, sql) {
  let descriptionCount = 0
  const descriptionPattern = /\(N'(TABLE|COLUMN|CONSTRAINT|INDEX)',\s*N'([a-z][a-z0-9_]*)',\s*(NULL|N'([a-z$][a-z0-9_$]*)'),\s*N'([^']+)'\)/gi
  for (const match of sql.matchAll(descriptionPattern)) {
    descriptionCount++
    const type = match[1].toUpperCase()
    const table = match[2].toLowerCase()
    const object = match[3].toUpperCase() === 'NULL' ? null : match[4].toLowerCase()
    const description = match[5].trim()
    const key = `${type}:${table}:${object ?? ''}`
    if (!description) report(file, `${type} ${table}.${object ?? ''} 的MS_Description不能为空`)
    if ((type === 'TABLE' || type === 'COLUMN') && !/^[\u3400-\u9fff（）]+$/.test(description)) {
      report(file, `${type} ${table}.${object ?? ''} 的MS_Description必须是简洁中文名称`)
    }
    if (type === 'TABLE' && !/^[\u3400-\u9fff（）]+表$/.test(description)) {
      report(file, `TABLE ${table} 的中文名称必须以“表”结尾`)
    }
    describedObjects.set(key, { file, description })
  }
  if (descriptionCount > 0 && !/sys\.sp_(?:add|update)extendedproperty/i.test(sql)) {
    report(file, '声明了MS_Description说明清单但未调用sys.sp_addextendedproperty或sys.sp_updateextendedproperty写入')
  }
}

for (const file of files) {
  const mainMatch = file.match(mainPattern)
  const patchMatch = file.match(patchPattern)
  if (!mainMatch && !patchMatch) {
    report(file, '文件名不符合业务主版本或归属主版本补丁命名规则')
    continue
  }

  const globalVersion = mainMatch?.[1] ?? patchMatch[1]
  const business = mainMatch?.[2] ?? patchMatch[2]
  if (versions.has(globalVersion)) {
    report(file, `全局版本V${globalVersion}已被${versions.get(globalVersion)}使用`)
  } else {
    versions.set(globalVersion, file)
  }

  const sql = readFileSync(resolve(migrationDirectory, file), 'utf8')
  collectPersistedDescriptions(file, sql)
  requireHeader(file, sql, '业务域', business)
  requireHeader(file, sql, '变更说明')
  requireHeader(file, sql, '需求依据')
  requireHeader(file, sql, '数据边界')
  requireHeader(file, sql, '时间规则')
  requireHeader(file, sql, '回退方案')

  if (mainMatch) {
    if (mainVersions.has(business)) {
      report(file, `业务域${business}已存在主版本${mainVersions.get(business).file}`)
    } else {
      mainVersions.set(business, { version: globalVersion, file })
    }
    requireHeader(file, sql, '主版本', `V${globalVersion}`)
    requireHeader(file, sql, '脚本类型', 'MAIN')
  } else {
    const parentVersion = patchMatch[3]
    const patchSequence = Number(patchMatch[4])
    requireHeader(file, sql, '脚本类型', 'PATCH')
    requireHeader(file, sql, '归属主版本', `V${parentVersion}`)
    requireHeader(file, sql, '补丁序号', patchMatch[4])
    const patches = patchesByBusiness.get(business) ?? []
    patches.push({ file, parentVersion, patchSequence })
    patchesByBusiness.set(business, patches)
  }

  if (/SELECT\s+\*/i.test(sql)) report(file, '禁止使用SELECT *')
  if (/NVARCHAR\s*\(\s*MAX\s*\)/i.test(sql)) report(file, '禁止无依据使用nvarchar(max)')
  checkDocumentedObjects(file, sql)
}

const createdObjectKeys = new Set(createdObjects.map(created =>
  `${created.type}:${created.table}:${created.object ?? ''}`
))

for (const created of createdObjects) {
  const key = `${created.type}:${created.table}:${created.object ?? ''}`
  if (!describedObjects.has(key)) {
    report(created.file, `${created.type} ${created.table}.${created.object ?? ''} 缺少可落库的MS_Description说明清单`)
  }
}

for (const [key, described] of describedObjects) {
  if (!createdObjectKeys.has(key)) {
    report(described.file, `MS_Description说明清单引用了不存在或未受管控的对象 ${key}`)
  }
}

for (const [business, patches] of patchesByBusiness) {
  const main = mainVersions.get(business)
  if (!main) {
    for (const patch of patches) report(patch.file, `业务域${business}没有主版本`)
    continue
  }
  if (patches.length > maximumPatchCount) {
    report(main.file, `业务域${business}已有${patches.length}个补丁，超过上限${maximumPatchCount}`)
  }
  const sorted = [...patches].sort((left, right) => left.patchSequence - right.patchSequence)
  sorted.forEach((patch, index) => {
    if (patch.parentVersion !== main.version) {
      report(patch.file, `归属主版本应为V${main.version}`)
    }
    if (Number(patch.file.slice(1, 5)) <= Number(patch.parentVersion)) {
      report(patch.file, '补丁全局版本必须大于归属主版本，确保Flyway按提交顺序执行')
    }
    if (patch.patchSequence !== index + 1) {
      report(patch.file, `补丁序号应连续，当前位置应为${String(index + 1).padStart(3, '0')}`)
    }
  })
}

if (errors.length) {
  console.error('SQL迁移静态检查失败：')
  errors.forEach(error => console.error(`- ${error}`))
  process.exit(1)
}

console.log(`SQL迁移静态检查通过：${mainVersions.size}个业务主版本，${files.length - mainVersions.size}个补丁。`)
