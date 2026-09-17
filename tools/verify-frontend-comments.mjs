import { readFileSync, readdirSync, statSync } from 'node:fs'
import { extname, join, relative } from 'node:path'
import process from 'node:process'

const projectRoot = new URL('..', import.meta.url).pathname
const sourceRoots = ['web-admin/src', 'web-embed/src']
const failures = []

/** Recursively returns source files covered by the frontend comment gate. */
function sourceFiles(directory) {
  return readdirSync(directory)
    .flatMap(name => {
      const path = join(directory, name)
      return statSync(path).isDirectory() ? sourceFiles(path) : [path]
    })
    .filter(path => ['.ts', '.vue'].includes(extname(path)))
}

/** Returns the closest non-empty source line before the supplied zero-based index. */
function previousContentLine(lines, index) {
  for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
    if (lines[cursor].trim()) return lines[cursor].trim()
  }
  return ''
}

for (const sourceRoot of sourceRoots) {
  const absoluteRoot = join(projectRoot, sourceRoot)
  for (const file of sourceFiles(absoluteRoot)) {
    const displayPath = relative(projectRoot, file)
    const contents = readFileSync(file, 'utf8')
    const lines = contents.split(/\r?\n/)
    if (sourceRoot === 'web-admin/src' && /(?:from\s+|import\s*)['"](?:\.\.\/){2,}/.test(contents)) {
      failures.push(`${displayPath}: 跨模块导入必须使用 @/，禁止两级以上相对路径`)
    }
    if (file.endsWith('.vue') && !contents.trimStart().startsWith('<!--')) {
      failures.push(`${displayPath}: Vue 组件顶部缺少职责注释`)
    }
    if (!file.endsWith('.ts')) continue
    lines.forEach((line, index) => {
      if (!/^export\s+(?:declare\s+)?(?:abstract\s+)?(?:type|interface|class|const|function|async\s+function)\b/.test(line.trim())) return
      const previousLine = previousContentLine(lines, index)
      if (previousLine !== '*/' && !/^\/\*\*.+\*\/$/.test(previousLine)) {
        failures.push(`${displayPath}:${index + 1}: 导出声明前缺少 TSDoc`)
      }
    })
  }
}

if (failures.length) {
  console.error('前端注释门禁失败：')
  failures.forEach(failure => console.error(`- ${failure}`))
  process.exit(1)
}

console.log('前端注释门禁通过。')
