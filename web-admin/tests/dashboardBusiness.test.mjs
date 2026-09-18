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
