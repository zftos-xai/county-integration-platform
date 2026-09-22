import { readFileSync, readdirSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const projectRoot = fileURLToPath(new URL('..', import.meta.url))
const sourceRoot = join(projectRoot, 'backend/src/main/java')
const violations = []
const endpointExceptions = new Map([
  ['BootstrapController.java', 2],
  ['PlatformInfoController.java', 1],
  ['SessionController.java', 5],
])

function verifyEndpointPermissions(source, path, filename) {
  const lines = source.split('\n')
  const requestMappingCount = lines.filter(line => /^\s*@RequestMapping\b/.test(line)).length
  if (requestMappingCount !== 1) {
    violations.push(`${path} 仅允许一个类级 @RequestMapping；方法入口使用明确的HTTP映射注解`)
  }
  let endpointCount = 0
  for (let index = 0; index < lines.length; index += 1) {
    if (!/^\s*@(Get|Post|Put|Patch|Delete)Mapping\b/.test(lines[index])) continue
    endpointCount += 1
    if (endpointExceptions.has(filename)) continue

    let protectedByPermission = false
    let reachedMethod = false
    for (let next = index + 1; next < lines.length; next += 1) {
      if (/^\s*@PreAuthorize\b/.test(lines[next])) protectedByPermission = true
      if (/^\s*(?:(?:public|protected|private)\s+)?[\w<>?,.\[\]]+\s+\w+\s*\(/.test(lines[next])) {
        reachedMethod = true
        break
      }
    }
    if (!reachedMethod || !protectedByPermission) {
      violations.push(`${path}:${index + 1} 业务入口缺少方法级 @PreAuthorize`)
    }
  }
  const expectedExceptions = endpointExceptions.get(filename)
  if (expectedExceptions !== undefined && endpointCount !== expectedExceptions) {
    violations.push(`${path} 例外入口数量由 ${expectedExceptions} 变为 ${endpointCount}，须逐个复核后更新白名单`)
  }
}

function inspect(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) {
      inspect(path)
    } else if (entry.name.endsWith('Controller.java')) {
      const source = readFileSync(path, 'utf8')
      if (/\bnew\s+AccessActor\s*\(/.test(source)) {
        violations.push(relative(projectRoot, path))
      }
      verifyEndpointPermissions(source, relative(projectRoot, path), entry.name)
    }
  }
}

inspect(sourceRoot)
if (violations.length > 0) {
  console.error('Controller 边界检查失败：')
  for (const path of violations) console.error(`- ${path}`)
  process.exitCode = 1
} else {
  console.log('Controller 边界检查通过：业务入口均声明功能权限，公开/会话入口数量与白名单一致，且未手工构造 AccessActor。')
}
