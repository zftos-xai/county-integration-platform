import { access, readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import process from 'node:process'

const projectRoot = resolve(import.meta.dirname, '..')
const deliveryStatusPath = resolve(projectRoot, 'web-admin/menu-delivery-status.json')
const allowedStages = new Set(['仅有计划', '后端已实现', '前端已接入', '已验证写入和回读', '业务已验收'])
const stageRank = new Map([...allowedStages].map((stage, index) => [stage, index]))

function fail(message) {
  console.error(`菜单交付检查失败：${message}`)
  process.exitCode = 1
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

async function existingFile(relativePath, menuLabel, field) {
  if (!relativePath) {
    fail(`${menuLabel} 缺少 ${field}`)
    return null
  }
  const absolutePath = resolve(projectRoot, relativePath)
  try {
    await access(absolutePath)
    return readFile(absolutePath, 'utf8')
  } catch {
    fail(`${menuLabel} 的 ${field} 文件不存在：${relativePath}`)
    return null
  }
}

const deliveryStatus = JSON.parse(await readFile(deliveryStatusPath, 'utf8'))
const routerSource = await readFile(resolve(projectRoot, 'web-admin/src/router/index.ts'), 'utf8')
const layoutSource = await readFile(resolve(projectRoot, 'web-admin/src/layout/AppLayout.vue'), 'utf8')
const ids = new Set()
const routes = new Set()

for (const menu of deliveryStatus.menus ?? []) {
  if (!menu.id || ids.has(menu.id)) fail(`菜单 id 缺失或重复：${menu.id ?? '(empty)'}`)
  if (!menu.route || routes.has(menu.route)) fail(`${menu.label ?? menu.id} 页面地址缺失或重复：${menu.route ?? '(空)'}`)
  ids.add(menu.id)
  routes.add(menu.route)

  if (!allowedStages.has(menu.stage)) fail(`${menu.label} 使用未知阶段：${menu.stage}`)
  const routePath = menu.route === '/' ? '' : menu.route.slice(1)
  const routePattern = menu.permission
    ? new RegExp(`path:\\s*'${escapeRegExp(routePath)}',[\\s\\S]{0,400}?requiredPermission:\\s*'${escapeRegExp(menu.permission)}'`)
    : new RegExp(`path:\\s*'${escapeRegExp(routePath)}',[\\s\\S]{0,300}?public:\\s*false`)
  const navigationPattern = menu.permission
    ? new RegExp(`\\{\\s*to:\\s*'${escapeRegExp(menu.route)}',\\s*label:\\s*'${escapeRegExp(menu.label)}',[^}]*permission:\\s*'${escapeRegExp(menu.permission)}'\\s*,?\\s*\\}`)
    : new RegExp(`\\{\\s*to:\\s*'${escapeRegExp(menu.route)}',\\s*label:\\s*'${escapeRegExp(menu.label)}',[^}]*\\}`)
  if (!routePattern.test(routerSource)) fail(`${menu.label} 未注册符合权限要求的正式页面地址 ${menu.route}`)
  if (routerSource.includes(`path: '${routePath}', component: PlaceholderView`)) fail(`${menu.label} 仍指向占位页面`)
  if (!navigationPattern.test(layoutSource)) fail(`${menu.label} 未按接口规范进入正式导航`)

  const pageSource = await existingFile(menu.page, menu.label, '页面')
  await existingFile(menu.api, menu.label, '接口调用代码')
  if (pageSource && !pageSource.trimStart().startsWith('<!--')) fail(`${menu.label} 页面缺少顶部职责注释`)

  let editorSource = null
  if (menu.form || menu.editor) {
    await existingFile(menu.form, menu.label, '表单数据代码')
    editorSource = await existingFile(menu.editor, menu.label, '编辑页面')
  }
  if (menu.writeCapable) {
    if (!pageSource?.includes('AuditAwareSuccess')) fail(`${menu.label} 写入成功后缺少审计追溯入口`)
    if (!pageSource?.includes('@reload=')) fail(`${menu.label} 缺少并发冲突重新读取交互`)
    if (!editorSource?.includes('status === 409')) fail(`${menu.label} 编辑器缺少 409 冲突说明和恢复动作`)
  }
  if (!Array.isArray(menu.tests) || menu.tests.length === 0) fail(`${menu.label} 未声明自动化测试`)
  else for (const testPath of menu.tests) await existingFile(testPath, menu.label, '自动化测试')

  const auditSource = await existingFile(menu.auditSource, menu.label, '审计记录代码')
  for (const action of menu.auditActions ?? []) {
    if (auditSource && !auditSource.includes(action)) fail(`${menu.label} 缺少审计动作或查询定义：${action}`)
  }

  if ((stageRank.get(menu.stage) ?? -1) >= (stageRank.get('已验证写入和回读') ?? 3)) {
    await existingFile(menu.evidence, menu.label, '写入、重新读取和审计的验收记录')
  }
  if (menu.stage === '业务已验收' && !menu.acceptanceDecision) {
    fail(`${menu.label} 标记“业务已验收”，但未记录验收决定`)
  }
}

if (!process.exitCode) console.log(`菜单交付检查通过：已核对 ${deliveryStatus.menus.length} 个正式菜单。`)
