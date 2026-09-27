import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const dashboardPath = 'web-admin/src/views/dashboard/DashboardView.vue'

test('运行总览只请求当前用户具备读取权限的业务数据', async () => {
  const source = await readFile(dashboardPath, 'utf8')
  for (const permission of ['organization:read', 'identity:read', 'access:read', 'configuration:read', 'audit:read']) {
    assert.ok(source.includes(`hasPermission('${permission}')`), `运行总览缺少 ${permission} 请求边界`)
  }
  assert.match(source, /Promise\.allSettled/)
  assert.match(source, /failureCount/)
})

test('运行总览使用真实管理接口并提供已实现业务下钻', async () => {
  const source = await readFile(dashboardPath, 'utf8')
  for (const api of ['listOrganizations', 'listUsers', 'listRoles', 'listParameterDefinitions', 'listParameterValues', 'listDictionaryTypes', 'listManagementAuditEvents']) {
    assert.ok(source.includes(api), `运行总览缺少真实接口 ${api}`)
  }
  for (const route of ['/organizations', '/users', '/roles', '/parameters', '/dictionaries', '/audit']) {
    assert.ok(source.includes(route), `运行总览缺少业务下钻 ${route}`)
  }
  assert.doesNotMatch(source, /<button disabled>|等待真实|接入后显示|暂无数据/)
})

test('运行总览处理会话失效、局部失败和请求取消', async () => {
  const source = await readFile(dashboardPath, 'utf8')
  assert.match(source, /error\.status === 401/)
  assert.match(source, /REQUEST_ABORTED/)
  assert.match(source, /dashboardController\?\.abort\(\)/)
})

test('运行总览的审计读取失败不显示成无管理操作', async () => {
  const source = await readFile(dashboardPath, 'utf8')
  const errorState = source.indexOf("v-else-if=\"failureLabels.includes('最近管理操作')\"")
  const emptyState = source.indexOf('v-else-if="recentAuditEvents.length === 0"')

  assert.ok(errorState >= 0 && emptyState > errorState)
  assert.match(source, /最近管理操作暂时无法读取/)
})

test('运行总览桌面版压缩标题区、横排操作并紧凑展示快捷入口', async () => {
  const [source, shell, styles] = await Promise.all([
    readFile(dashboardPath, 'utf8'),
    readFile('web-admin/src/layout/AppLayout.vue', 'utf8'),
    readFile('web-admin/src/assets/styles/prototype.css', 'utf8'),
  ])
  assert.match(shell, /'dashboard-page-shell': route\.path === '\/'/)
  assert.match(styles, /\.prototype-main\.dashboard-page-shell \.prototype-topbar \{ min-height: 0;/)
  assert.match(source, /\.dashboard-audit-card \.card-header > \.audit-card-actions \{ display: flex;/)
  assert.match(source, /\.overview-work-grid \{ align-items: start;/)
  assert.match(styles, /\.overview-exchange-list small \{[^}]*white-space: normal; overflow-wrap: anywhere;/)
  assert.match(source, /\.dashboard-shortcuts \.shortcut-grid \{ grid-template-columns: repeat\(2, minmax\(0, 1fr\)\);/)
})

test('运行总览手机端每张指标卡之间保留分隔线', async () => {
  const source = await readFile(dashboardPath, 'utf8')
  const mobileRules = source.match(/@media \(max-width: 480px\) \{([\s\S]*?)\n\}/)?.[1] ?? ''

  assert.match(mobileRules, /\.overview-kpis > a:not\(:last-child\) \{ border-bottom: 1px solid #e3e8eb; \}/)
  assert.match(mobileRules, /\.overview-kpis > a:last-child \{ border-bottom: 0; \}/)
})

test('运行总览显示基础数据同步审计操作的中文名称', async () => {
  const source = await readFile('web-admin/src/utils/managementDisplay.ts', 'utf8')
  for (const entry of [
    "MASTER_DATA_ICD10_SYNCED: 'ICD-10同步结束'",
    "MASTER_DATA_BATCH_RUN_STARTED: '开始同步'",
    "MASTER_DATA_BATCH_RECOVERED: '中断批次收尾'",
  ]) assert.ok(source.includes(entry), `审计操作名称缺少映射：${entry}`)
})
