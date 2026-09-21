import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const projectRoot = fileURLToPath(new URL('..', import.meta.url))
const mapperRoot = join(projectRoot, 'backend', 'src', 'main', 'resources', 'mapper')
const mappingElementPattern = /<(idArg|arg|id|result|association|collection)\b/g
const sqlBlockPattern = /<(sql|select|insert|update|delete)\b([^>]*)>([\s\S]*?)<\/\1>/g
const reusableColumnListPattern = /<sql\b([^>]*)>([\s\S]*?)<\/sql>/g
const insertColumnListPattern = /INSERT\s+INTO\s+[\w.]+(?:\s|<!--[\s\S]*?-->)*\(([\s\S]*?)\)\s*(?:VALUES|SELECT)/gi
const immediatelyPrecedingCommentPattern = /<!--([\s\S]*?)-->\s*$/
const commentPattern = /<!--\s*([^]*?\S)\s*-->/
const allCommentsPattern = /<!--\s*([^]*?\S)\s*-->/g
const failures = []

/** Returns all production MyBatis mapper XML files in stable path order. */
function findMapperFiles(directory) {
  return readdirSync(directory, { withFileTypes: true })
    .flatMap((entry) => {
      const entryPath = join(directory, entry.name)
      if (entry.isDirectory()) return findMapperFiles(entryPath)
      return entry.isFile() && entry.name.endsWith('Mapper.xml') ? [entryPath] : []
    })
    .sort()
}

/** Returns the one-based source line containing the supplied character offset. */
function lineNumber(source, offset) {
  return source.slice(0, offset).split('\n').length
}

/** Records a mapper comment violation with a repository-relative source location. */
function fail(file, source, offset, message) {
  failures.push(`${relative(projectRoot, file)}:${lineNumber(source, offset)} ${message}`)
}

/** Verifies that each result mapping field has its own adjacent business comment. */
function verifyResultMappings(file, source) {
  for (const match of source.matchAll(mappingElementPattern)) {
    const precedingComment = source.slice(0, match.index).match(immediatelyPrecedingCommentPattern)
    if (!precedingComment || !precedingComment[1].trim()) {
      fail(file, source, match.index, `<${match[1]}> 字段映射前缺少紧邻的业务注释`)
    }
  }
}

/** Verifies that reusable SQL and every database operation explain their field semantics. */
function verifySqlBlocks(file, source) {
  for (const match of source.matchAll(sqlBlockPattern)) {
    if (!commentPattern.test(match[3])) {
      const statementId = match[2].match(/\bid="([^"]+)"/)?.[1]
      const label = statementId ? `<${match[1]} id="${statementId}">` : `<${match[1]}>`
      fail(file, source, match.index, `${label} 内缺少字段或业务条件注释`)
    }
  }
}

/** Returns field and comment counts for a comma-separated SQL field list. */
function summarizeFieldList(fieldList) {
  const commentTexts = [...fieldList.matchAll(allCommentsPattern)]
    .map((match) => match[1].replaceAll(/\s+/g, ' ').trim())
  const comments = commentTexts.length
  const uniqueComments = new Set(commentTexts).size
  const fields = fieldList
    .replaceAll(allCommentsPattern, '')
    .split(',')
    .map((field) => field.trim())
    .filter(Boolean).length
  return { comments, fields, uniqueComments }
}

/** Requires reusable result columns and inserted columns to document every field. */
function verifyFieldLists(file, source) {
  for (const match of source.matchAll(reusableColumnListPattern)) {
    const fragmentId = match[1].match(/\bid="([^"]+)"/)?.[1]
    if (!fragmentId?.endsWith('Columns')) continue
    const { comments, fields, uniqueComments } = summarizeFieldList(match[2])
    if (comments < fields || uniqueComments < fields) {
      fail(file, source, match.index,
        `<sql id="${fragmentId}"> 有 ${fields} 个字段、${comments} 条注释、${uniqueComments} 条不同注释，必须逐字段准确说明`)
    }
  }

  for (const match of source.matchAll(insertColumnListPattern)) {
    const { comments, fields, uniqueComments } = summarizeFieldList(match[1])
    if (comments < fields || uniqueComments < fields) {
      fail(file, source, match.index,
        `INSERT 字段清单有 ${fields} 个字段、${comments} 条注释、${uniqueComments} 条不同注释，必须逐字段准确说明`)
    }
  }
}

for (const file of findMapperFiles(mapperRoot)) {
  const source = readFileSync(file, 'utf8')
  verifyResultMappings(file, source)
  verifySqlBlocks(file, source)
  verifyFieldLists(file, source)
}

if (failures.length > 0) {
  console.error('MyBatis Mapper XML 注释自动检查失败：')
  for (const failure of failures) console.error(`- ${failure}`)
  process.exit(1)
}

console.log('MyBatis Mapper XML 注释自动检查通过。')
